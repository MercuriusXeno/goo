package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * The server's pipeline for item goo values: loads base values from JSON,
 * derives values from all recipe types using LCD rule, persists derived values,
 * and publishes each result as a {@link GooValueTable} readers look up through
 * {@link #table()}. One lives on each running server
 * (decision type-package-and-per-server-holders).
 *
 * <p>The derivation engine is split into two layers: a thin Minecraft adapter
 * (recipe/ingredient resolution) and a pure logic core that operates on
 * {@link RecipeInput} records, enabling unit testing without a running server.</p>
 *
 * <p>Heavy lifting is delegated to package-private helpers:
 * {@link GooValueLoader} (parsing), {@link GooValueMerger} (datapack merging),
 * {@link GooValueValidator} (authoring checks), {@link GooValueRecipeAdapter}
 * (MC recipe conversion), and {@link GooValueCache} (disk persistence).</p>
 */
public class GooValueRegistry {

    static final int MAX_DERIVATION_PASSES = 20;

    /**
     * Log: derived values from recipes.
     */
    private static final String LOG_DERIVED = "Derived {} goo values from recipes";
    /**
     * Warning: no base values loaded yet.
     */
    private static final String WARN_NO_BASE_VALUES = "No base values loaded. Run /goo regen first.";
    /**
     * Error: no datapack provides base values.
     */
    private static final String ERROR_NO_DATAPACK = "No datapack provides goo_values/base_values.json";

    private final Map<Identifier, GooValue> baseValues = new HashMap<>();
    /**
     * The values readers look up: an immutable table replaced whole, never
     * mutated, so a reader on another thread holds one consistent snapshot
     * (decision diagnose-then-fix-server-link-and-value-race).
     */
    private volatile GooValueTable table = GooValueTable.EMPTY;
    /**
     * Items explicitly denied a value (e.g. ore blocks - fortune makes them unvaluable).
     */
    private final Set<Identifier> deniedItems = new HashSet<>();
    /**
     * Items restricted from plexer reconstitution but still decomposable.
     */
    private final Set<Identifier> restrictedItems = new HashSet<>();
    /**
     * Named constants from _constants block, resolved during value parsing.
     */
    private final Map<String, Integer> constants = new HashMap<>();
    /**
     * Tree constants from _constants block: GooValue objects keyed by name.
     */
    private final Map<String, GooValue> treeConstants = new HashMap<>();
    /**
     * Pseudo-tags from _groups block: group name to item set.
     */
    private final Map<String, Set<Identifier>> pseudoTags = new HashMap<>();
    /**
     * Post-derivation conversions from _post_conversions block.
     */
    private GooConversion.ParsedConversions postConversions;
    /**
     * Result of the last derivation or cache load. Null before first derivation.
     */
    private @Nullable DerivationResult lastDerivation;
    /**
     * Cached recipe inputs from the last derivation, for scaffold generation.
     */
    private List<RecipeInput> lastRecipes = List.of();
    /**
     * Last merged base_values JSON from regen, retained for validation.
     */
    private @Nullable JsonObject lastMergedBaseValues;

    private Path effectiveCachePath;

    /**
     * Loads the datapack resource stack for base_values.json.
     *
     * @param server the server providing the resource manager
     * @return the ordered resource stack
     */
    private static List<Resource> loadResourceStack(MinecraftServer server) {
        ResourceManager resourceManager = server.getResourceManager();
        Identifier location = Identifier.fromNamespaceAndPath(
                GooValueLoader.modNamespace(), GooValueLoader.baseValuesResource());
        return resourceManager.getResourceStack(location);
    }

    /**
     * Sets the path for the effective value cache file.
     *
     * @param path the filesystem path for caching effective values
     */
    public void setEffectiveCachePath(Path path) {
        this.effectiveCachePath = path;
    }

    /**
     * Loads base values by merging all datapack layers via the server's ResourceManager.
     *
     * @param server the running server whose resource manager provides the pack stack
     */
    public void loadBaseValuesFromPacks(MinecraftServer server) {
        Map<Identifier, GooValue> loadedEffective = new HashMap<>();
        var state = createParseState(loadedEffective);
        GooValueLoader.clearRegistryState(state);
        List<Resource> stack = loadResourceStack(server);
        if (stack.isEmpty()) {
            Goo.LOGGER.error(ERROR_NO_DATAPACK);
            publishEffectiveValues(loadedEffective);
            return;
        }
        applyPackLayers(stack, state);
        publishEffectiveValues(loadedEffective);
    }

    /**
     * Creates a fresh ParseState backed by this registry's maps and a scratch
     * effective map the caller publishes once the load completes.
     *
     * @param loadedEffective the scratch map the load fills with effective values
     * @return a new ParseState wired to this registry's mutable maps
     */
    private GooValueLoader.ParseState createParseState(Map<Identifier, GooValue> loadedEffective) {
        return new GooValueLoader.ParseState(
                baseValues, loadedEffective, deniedItems, restrictedItems,
                constants, treeConstants, pseudoTags);
    }

    /**
     * Publishes a fresh table of the given effective values beside the base,
     * denied and restricted items, in one write, so no reader sees a half-built table.
     *
     * @param built the complete effective values
     */
    private void publishEffectiveValues(Map<Identifier, GooValue> built) {
        table = new GooValueTable(built, baseValues.keySet(), deniedItems, restrictedItems);
    }

    /**
     * Answers the values this registry last published.
     *
     * @return the current table
     */
    public GooValueTable table() {
        return table;
    }

    /**
     * Parses and applies merged datapack layers, storing conversion state.
     *
     * @param stack the resource stack to merge
     * @param state the parse state to populate
     */
    private void applyPackLayers(List<Resource> stack, GooValueLoader.ParseState state) {
        var layers = GooValueLoader.parseResourceLayers(stack);
        GooValueLoader.applyMergedLayers(layers, state);
        postConversions = state.postConversions;
        lastMergedBaseValues = state.lastMergedBaseValues;
    }

    /**
     * Derives goo values from all server recipes using the LCD rule.
     *
     * @param server the running server providing recipes
     * @return the number of items that received derived values
     */
    public int deriveFromRecipes(MinecraftServer server) {
        HolderLookup.Provider registries = server.registryAccess();
        lastRecipes = GooValueRecipeAdapter.adaptRecipes(
                server.getRecipeManager().getRecipes(), registries);
        boolean baseOverride = GooConfig.BASE_VALUES_OVERRIDE_RECIPES.get();
        int derived = deriveFromRecipeInputs(lastRecipes, baseOverride);
        Goo.LOGGER.info(LOG_DERIVED, derived);
        return derived;
    }

    /**
     * Loads effective values from the flat cache file.
     * Also serves as the reload entry point.
     */
    public void loadEffectiveCache() {
        Map<Identifier, GooValue> loaded = new HashMap<>(table.getEffectiveValues());
        GooValueCache.loadEffectiveCache(effectiveCachePath, loaded);
        publishEffectiveValues(loaded);
    }

    /**
     * Saves the complete effective value map to the cache file.
     */
    public void saveEffectiveValues() {
        GooValueCache.saveEffectiveValues(effectiveCachePath, table.getEffectiveValues());
    }

    /**
     * Validates the last-loaded base_values.json for authoring mistakes.
     *
     * @return list of validation warnings
     */
    public List<String> validateBaseValues() {
        List<String> warnings = new ArrayList<>();
        if (lastMergedBaseValues == null) {
            warnings.add(WARN_NO_BASE_VALUES);
            return warnings;
        }
        GooValueValidator.validateJson(lastMergedBaseValues, warnings);
        return warnings;
    }

    /**
     * Generates scaffold by collecting recipes fresh from the server.
     *
     * @param server the running server providing recipes
     * @param bare   if true, emit only root keys with empty values
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldFresh(MinecraftServer server, boolean bare) {
        HolderLookup.Provider registries = server.registryAccess();
        List<RecipeInput> recipes = GooValueRecipeAdapter.adaptRecipes(
                server.getRecipeManager().getRecipes(), registries);
        Set<Identifier> allItems = BuiltInRegistries.ITEM.keySet();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                recipes, baseValues, deniedItems, allItems);
        return ScaffoldGenerator.generateScaffold(roots, recipes, bare);
    }

    /**
     * Generates scaffold from cached recipes (requires a prior regen or load).
     *
     * @param bare if true, emit only root keys with empty values
     * @return scaffold result with lines and root count
     */
    public ScaffoldGenerator.ScaffoldResult generateScaffoldMissing(boolean bare) {
        Set<Identifier> allItems = BuiltInRegistries.ITEM.keySet();
        List<ScaffoldGenerator.Root> roots = ScaffoldGenerator.findRoots(
                lastRecipes, table.getEffectiveValues(), deniedItems, allItems);
        return ScaffoldGenerator.generateScaffold(roots, lastRecipes, bare);
    }

    /**
     * Returns a snapshot of diagnostic data from the last derivation run.
     * Consolidates baseSize, derivedSize, cycles, conflicts, divisibility losses,
     * derivation sources, and all referenced IDs into one accessor.
     *
     * @return diagnostic snapshot (never null; counts are zero before first derivation)
     */
    public DiagnosticSnapshot diagnostics() {
        Set<Identifier> allRefs = new HashSet<>(baseValues.keySet());
        allRefs.addAll(deniedItems);
        allRefs.addAll(restrictedItems);
        return buildSnapshot(Collections.unmodifiableSet(allRefs));
    }

    /**
     * Builds the diagnostic snapshot, using empty defaults when no derivation has run yet.
     *
     * @param allRefs the complete set of referenced item identifiers
     * @return the diagnostic snapshot
     */
    private DiagnosticSnapshot buildSnapshot(Set<Identifier> allRefs) {
        if (lastDerivation == null) {
            return new DiagnosticSnapshot(
                    baseValues.size(), 0, List.of(), List.of(), List.of(), allRefs, Map.of());
        }
        return new DiagnosticSnapshot(
                baseValues.size(),
                lastDerivation.derivedValues().size(),
                lastDerivation.cycles(),
                lastDerivation.conflicts(),
                lastDerivation.divisibilityLosses(),
                allRefs,
                lastDerivation.derivationSources()
        );
    }

    /**
     * Derives goo values from MC-free recipe inputs using the LCD rule.
     *
     * @param recipes      all recipes to consider
     * @param baseOverride when true, base values always win over derived values
     * @return the number of items that received derived values
     */
    int deriveFromRecipeInputs(List<RecipeInput> recipes, boolean baseOverride) {
        lastDerivation = GooValueDerivation.derive(recipes, baseValues, deniedItems, baseOverride);
        Map<Identifier, GooValue> derivedEffective = new HashMap<>(lastDerivation.effectiveValues());
        GooConversionLoader.applyConversions(postConversions, derivedEffective, pseudoTags);
        publishEffectiveValues(derivedEffective);
        return lastDerivation.derivedValues().size();
    }

    /**
     * Replaces the base values and publishes them as the effective values, the
     * state a pack load leaves before any derivation. A seam for tests.
     *
     * @param values the base values
     */
    void seedBaseValues(Map<Identifier, GooValue> values) {
        baseValues.clear();
        baseValues.putAll(values);
        publishEffectiveValues(values);
    }

    /**
     * Replaces the denied items. A seam for tests.
     *
     * @param items the items denied a value
     */
    void seedDeniedItems(Set<Identifier> items) {
        deniedItems.clear();
        deniedItems.addAll(items);
        publishEffectiveValues(table.getEffectiveValues());
    }

    /**
     * Parses one base_values.json stream into this registry's state and
     * publishes what it loaded. A seam for tests.
     *
     * @param stream the JSON stream
     * @throws IOException when the stream cannot be read
     */
    void parseBaseValues(InputStream stream) throws IOException {
        Map<Identifier, GooValue> loadedEffective = new HashMap<>(table.getEffectiveValues());
        var state = createParseState(loadedEffective);
        GooValueLoader.parseBaseValuesFromStream(stream, state);
        postConversions = state.postConversions;
        publishEffectiveValues(loadedEffective);
    }

    /**
     * Copies the base values over the effective values and applies the
     * post-conversions, as a load with no recipes would. A seam for tests.
     */
    void publishBaseAsEffective() {
        Map<Identifier, GooValue> effective = new HashMap<>(table.getEffectiveValues());
        effective.putAll(baseValues);
        GooConversionLoader.applyConversions(postConversions, effective, pseudoTags);
        publishEffectiveValues(effective);
    }

    /**
     * Answers the values the last derivation derived from recipes.
     *
     * @return the derived values, empty before any derivation
     */
    Map<Identifier, GooValue> lastDerivedValues() {
        return lastDerivation != null ? lastDerivation.derivedValues() : Map.of();
    }

    /**
     * A strongly connected component in the recipe dependency graph.
     *
     * @param items     the items forming the cycle
     * @param hasAnchor whether the cycle contains a hand-keyed anchor value
     * @param anchor    the anchor item, or null if no anchor
     */
    public record RecipeCycle(List<Identifier> items, boolean hasAnchor, @Nullable Identifier anchor) {
    }

    /**
     * A disagreement between a hand-keyed base value and a recipe-derived value.
     *
     * @param item        the conflicting item
     * @param baseValue   the hand-keyed base value
     * @param recipeValue the recipe-derived value
     */
    public record ValueConflict(Identifier item, GooValue baseValue, GooValue recipeValue) {
        /**
         * Returns true if the recipe path produces fewer total goo than the base value.
         *
         * @return true if derived is cheaper than hand-keyed
         */
        public boolean isRecipeCheaper() {
            return recipeValue.totalGoo() < baseValue.totalGoo();
        }
    }

    /**
     * A recipe where integer division causes value loss in the output.
     *
     * @param output       the output item
     * @param outputCount  the recipe output count
     * @param inputTotal   the total input value in goo
     * @param perItemValue the per-item value after division
     * @param lostGoo    the goo lost to integer truncation
     * @param recipe       the source recipe input
     */
    public record DivisibilityLoss(Identifier output, int outputCount, int inputTotal,
                                   int perItemValue, int lostGoo, RecipeInput recipe) {
    }

    /**
     * Snapshot of diagnostic data from the last derivation run.
     * Returned by {@link #diagnostics()} to consolidate accessors.
     *
     * @param baseSize           number of hand-keyed base values
     * @param derivedSize        number of recipe-derived values
     * @param cycles             recipe dependency cycles
     * @param conflicts          base/derived value conflicts
     * @param divisibilityLosses recipes with integer division loss
     * @param allReferencedIds   all item IDs in base_values.json (valued + denied + restricted)
     * @param derivationSources  map from derived item ID to the recipe that produced its value
     */
    public record DiagnosticSnapshot(
            int baseSize, int derivedSize,
            List<RecipeCycle> cycles,
            List<ValueConflict> conflicts,
            List<DivisibilityLoss> divisibilityLosses,
            Set<Identifier> allReferencedIds,
            Map<Identifier, RecipeInput> derivationSources
    ) {
    }

}

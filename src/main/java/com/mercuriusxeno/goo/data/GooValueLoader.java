package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Parses base_values.json into registry state: constants, groups (pseudo-tags),
 * item values, conversions, and restrictions. Handles datapack loading,
 * pseudo-tag expansion, and parallel copy.
 * Pure-static utility with no instance state.
 */
final class GooValueLoader {

    static final String PREFIX_TAG = "#";
    private static final String PREFIX_INTERNAL = "_";
    private static final String VALUE_DENIED = "denied";
    private static final String MOD_NAMESPACE = "goo";
    private static final String BASE_VALUES_RESOURCE = "goo_values/base_values.json";

    // --- Log messages ---

    private static final String LOG_LOADED_PACKS = "Loaded {} base goo values from {} pack(s)";
    private static final String LOG_READ_PACK_FAIL = "Failed to read base_values.json from pack {}: {}";
    private static final String LOG_NEGATIVE_BASE = "Negative goo in base value for {}: {} -- skipped";

    private GooValueLoader() {
    }

    /**
     * Parses constants, groups, item entries, and conversions from an input stream.
     *
     * @param is    the input stream containing base_values.json data
     * @param state mutable parsing state to populate
     * @throws IOException if reading the stream fails
     */
    static void parseBaseValuesFromStream(InputStream is, ParseState state) throws IOException {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            GooConstantParser.parseConstants(json, state);
            parseItemValues(json, state);
            GooConversionLoader.parseConversions(json, state);
        }
    }

    /**
     * Parses each resource in the stack into a JsonObject, skipping failures.
     *
     * @param stack ordered resource list from the pack stack
     * @return parsed JSON objects (failures skipped)
     */
    static List<JsonObject> parseResourceLayers(List<Resource> stack) {
        List<JsonObject> layers = new ArrayList<>();
        for (Resource resource : stack) {
            parseOneResourceLayer(resource, layers);
        }
        return layers;
    }

    /**
     * Parses a single resource into a JsonObject, appending to the list on success.
     *
     * @param resource the datapack resource to parse
     * @param layers   the accumulating list of parsed JSON layers
     */
    private static void parseOneResourceLayer(Resource resource, List<JsonObject> layers) {
        try (BufferedReader reader = resource.openAsReader()) {
            layers.add(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_READ_PACK_FAIL,
                        resource.sourcePackId(), e.getMessage());
            }
        }
    }

    /**
     * Merges, expands, and applies parsed JSON layers to parsing state.
     *
     * @param layers parsed JSON objects in pack order
     * @param state  mutable parsing state to populate
     */
    static void applyMergedLayers(List<JsonObject> layers, ParseState state) {
        applyMergedLayers(layers, state, GooValueMerger::resolveItemTag);
    }

    /**
     * Merges, expands and applies parsed JSON layers, reading item tags through the resolver handed in, so a
     * tool with no game registry expands tags from its own listing (decision workbench-authors-base-values-whole).
     *
     * @param layers      parsed JSON objects in pack order
     * @param state       mutable parsing state to populate
     * @param tagResolver resolves an item tag to its member ids
     */
    static void applyMergedLayers(List<JsonObject> layers, ParseState state,
                                  Function<Identifier, Set<Identifier>> tagResolver) {
        JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(layers);
        merged = GooValueMerger.expandTagEntries(merged, tagResolver);
        state.lastMergedBaseValues = merged;
        GooConstantParser.parseConstants(merged, state);
        parseItemValues(merged, state);
        GooConversionLoader.parseConversions(merged, state);
        state.effectiveValues.putAll(state.baseValues);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED_PACKS, state.baseValues.size(), layers.size());
        }
    }

    /**
     * Parses _groups first (pseudo-tags), then item entries. Resolves #name against pseudo-tags.
     *
     * @param json  the root JSON object to extract item values from
     * @param state mutable parsing state
     */
    private static void parseItemValues(JsonObject json, ParseState state) {
        GooGroupParser.parseGroups(json, state);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            classifyItemEntry(entry.getKey(), entry.getValue(), state);
        }
        settleWaiting(state);
        GooGroupParser.parseRestricted(json, state);
        GooGroupParser.parseRoots(json, state);
    }

    /**
     * Dispatches a single JSON entry to internal skip, pseudo-tag expansion, or item assignment.
     *
     * @param key   the JSON entry key (internal prefix, pseudo-tag, or item ID)
     * @param value the JSON value to parse
     * @param state mutable parsing state
     */
    private static void classifyItemEntry(String key, JsonElement value, ParseState state) {
        if (key.startsWith(PREFIX_INTERNAL)) {
            return;
        }
        if (key.startsWith(PREFIX_TAG)) {
            GooParallelCopy.expandPseudoTag(key.substring(1), value, state);
            return;
        }
        assignItemValue(Identifier.parse(key), value, state);
    }

    /**
     * Assigns a value or denial to a single item.
     *
     * @param itemId the item to assign a value to
     * @param value  the JSON value (object for explicit, string for expression, "denied")
     * @param state  mutable parsing state
     */
    static void assignItemValue(Identifier itemId, JsonElement value, ParseState state) {
        if (isDeniedEntry(value)) {
            state.deniedItems.add(itemId);
        } else {
            LackedTypeReads.Recorded<GooValue> resolved;
            try {
                resolved = LackedTypeReads.recording(() -> resolveItemEntry(value, state));
            } catch (UnresolvedItemReference unresolved) {
                // Decision diagnose-and-correct-item-reference-unrecognized-token: an entry naming an item no line
                // valued yet waits for it, a line below or the recipes valuing it.
                state.waitingItems.put(itemId, value);
                return;
            }
            noteLacked(itemId, resolved.lacked(), state.lackedTypes);
            keep(itemId, resolved.value(), state);
        }
    }

    // Decision lacked-type-log-line-leaves-and-the-workbench-line-stays: the entry keeps its value and the workbench
    // reads the referent and type it lacked; the game logs nothing.
    private static void noteLacked(Identifier itemId, @Nullable String lacked, Map<Identifier, String> lackedTypes) {
        if (lacked != null) {
            lackedTypes.put(itemId, lacked);
        }
    }

    private static void keep(Identifier itemId, GooValue resolved, ParseState state) {
        if (resolved.hasNegative()) {
            Goo.LOGGER.error(LOG_NEGATIVE_BASE, itemId, resolved);
            // The workbench reads why the item loads no value beside its entry rather than an empty value.
            state.negativeItems.put(itemId, resolved);
        } else {
            state.baseValues.put(itemId, resolved);
        }
    }

    // Once every line is read, each waiting entry reads the items a later line valued; what still names an item
    // holding no value waits for the recipes.
    private static void settleWaiting(ParseState state) {
        Settled settled = resolveWaiting(state.waitingItems, state.baseValues, state.constants, state.treeConstants);
        settled.valued().forEach((item, value) -> keep(item, value, state));
        state.lackedTypes.putAll(settled.lacked());
        state.waitingItems.keySet().removeAll(settled.valued().keySet());
        state.unresolvedItems.clear();
        state.unresolvedItems.putAll(settled.unresolved());
    }

    /**
     * What a pass over the waiting entries found.
     *
     * @param valued     each entry that now reads a value, with it
     * @param unresolved each entry still naming an item that holds no value, with that item
     * @param lacked     each valued entry that read a goo type its referent holds no amount of as zero, with the
     *                   error naming both (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line)
     */
    record Settled(Map<Identifier, GooValue> valued, Map<Identifier, String> unresolved,
                   Map<Identifier, String> lacked) {
    }

    /**
     * Evaluates each waiting entry against the values known, over and over while a pass values one more, so an
     * entry naming another waiting entry reads it once that one resolves (decision
     * diagnose-and-correct-item-reference-unrecognized-token).
     *
     * @param waiting       each waiting entry with its JSON value
     * @param known         the values the entries read, which gains each value a pass finds, a negative one aside
     * @param constants     the scalar constants
     * @param treeConstants the tree constants
     * @return the entries valued and those still waiting
     */
    static Settled resolveWaiting(Map<Identifier, JsonElement> waiting, Map<Identifier, GooValue> known,
                                  Map<String, GooFraction> constants, Map<String, GooValue> treeConstants) {
        Settled settled = new Settled(new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>(),
                new java.util.LinkedHashMap<>());
        int valuedBefore;
        do {
            valuedBefore = settled.valued().size();
            settled.unresolved().clear();
            for (Map.Entry<Identifier, JsonElement> entry : waiting.entrySet()) {
                if (!settled.valued().containsKey(entry.getKey())) {
                    settleEntry(entry, known, constants, treeConstants, settled);
                }
            }
        } while (settled.valued().size() > valuedBefore);
        return settled;
    }

    // One waiting entry evaluated against the values known, recorded where the evaluation leaves it.
    private static void settleEntry(Map.Entry<Identifier, JsonElement> entry, Map<Identifier, GooValue> known,
                                    Map<String, GooFraction> constants, Map<String, GooValue> treeConstants,
                                    Settled settled) {
        try {
            LackedTypeReads.Recorded<GooValue> evaluated = LackedTypeReads.recording(
                    () -> evaluateEntry(entry.getValue(), known, constants, treeConstants));
            GooValue value = evaluated.value();
            settled.valued().put(entry.getKey(), value);
            noteLacked(entry.getKey(), evaluated.lacked(), settled.lacked());
            if (!value.hasNegative()) {
                known.put(entry.getKey(), value);
            }
        } catch (UnresolvedItemReference reference) {
            settled.unresolved().put(entry.getKey(), reference.item());
        }
    }

    /**
     * Resolves a non-denied item entry: JSON object -> explicit GooValue,
     * string -> item reference expression.
     *
     * @param value the JSON element to resolve
     * @param state parsing state containing constants and base values
     * @return the parsed GooValue
     */
    private static GooValue resolveItemEntry(JsonElement value, ParseState state) {
        return evaluateEntry(value, state.baseValues, state.constants, state.treeConstants);
    }

    // An entry's value read against the item values handed in.
    private static GooValue evaluateEntry(JsonElement value, Map<Identifier, GooValue> known,
                                          Map<String, GooFraction> constants, Map<String, GooValue> treeConstants) {
        if (value.isJsonObject()) {
            return GooValueJsonFormat.parseGooValue(value.getAsJsonObject(), constants, known, treeConstants);
        }
        return GooValueExpression.evaluate(value.getAsString().trim(), known, constants, treeConstants);
    }

    /**
     * Returns true if the JSON value is the string "denied".
     *
     * @param element the JSON element to check
     * @return true if the element is the string "denied"
     */
    private static boolean isDeniedEntry(JsonElement element) {
        return element.isJsonPrimitive() && VALUE_DENIED.equals(element.getAsString());
    }

    /**
     * Clears all mutable registry state before a fresh load.
     *
     * @param state the parse state to clear
     */
    static void clearRegistryState(ParseState state) {
        state.baseValues.clear();
        state.deniedItems.clear();
        state.restrictedItems.clear();
        state.effectiveValues.clear();
        state.constants.clear();
        state.treeConstants.clear();
        state.pseudoTags.clear();
        state.negativeItems.clear();
        state.postConversions = null;
        state.lastMergedBaseValues = null;
    }

    /**
     * Returns the mod namespace string.
     *
     * @return "goo"
     */
    static String modNamespace() {
        return MOD_NAMESPACE;
    }

    /**
     * Returns the base values resource path within the datapack.
     *
     * @return "goo_values/base_values.json"
     */
    static String baseValuesResource() {
        return BASE_VALUES_RESOURCE;
    }

    /**
     * Mutable state bucket passed through parsing methods so the loader remains static.
     * Created by the registry before a load, populated during parsing, then read back.
     */
    static final class ParseState {
        final Map<Identifier, GooValue> baseValues;
        final Map<Identifier, GooValue> effectiveValues;
        final Set<Identifier> deniedItems;
        final Set<Identifier> restrictedItems;
        final Map<String, GooFraction> constants;
        final Map<String, GooValue> treeConstants;
        final Map<String, Set<Identifier>> pseudoTags;
        /**
         * The clique roots _roots names, in file order (decision audit-proposes-root-candidates-and-flags-bleed).
         */
        final Set<Identifier> rootItems = new java.util.LinkedHashSet<>();
        /**
         * Each item whose value held an amount below zero, which loads no value, with the value it resolved to.
         */
        final Map<Identifier, GooValue> negativeItems = new java.util.LinkedHashMap<>();
        /**
         * Each entry naming an item no line valued yet, which waits for that item, with its JSON value (decision
         * diagnose-and-correct-item-reference-unrecognized-token).
         */
        final Map<Identifier, JsonElement> waitingItems = new java.util.LinkedHashMap<>();
        /**
         * Each entry still waiting once every line is read, with the item it names that holds no value.
         */
        final Map<Identifier, String> unresolvedItems = new java.util.LinkedHashMap<>();
        /**
         * Each entry reading a goo type its referent holds no amount of, which reads that type as zero, with the error
         * naming both (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
         */
        final Map<Identifier, String> lackedTypes = new java.util.LinkedHashMap<>();
        GooConversion.ParsedConversions postConversions;
        JsonObject lastMergedBaseValues;

        ParseState(Map<Identifier, GooValue> baseValues,
                   Map<Identifier, GooValue> effectiveValues,
                   Set<Identifier> deniedItems,
                   Set<Identifier> restrictedItems,
                   Map<String, GooFraction> constants,
                   Map<String, GooValue> treeConstants,
                   Map<String, Set<Identifier>> pseudoTags) {
            this.baseValues = baseValues;
            this.effectiveValues = effectiveValues;
            this.deniedItems = deniedItems;
            this.restrictedItems = restrictedItems;
            this.constants = constants;
            this.treeConstants = treeConstants;
            this.pseudoTags = pseudoTags;
        }
    }
}

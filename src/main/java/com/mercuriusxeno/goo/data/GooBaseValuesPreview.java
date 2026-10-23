package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads base_values.json text through the game's own loader with no server, so a tool authoring the file
 * sees the values and groups the game would load (decision workbench-authors-base-values-whole).
 */
public final class GooBaseValuesPreview {

    // With no listing of the loadout's tags, a #tag entry no group answers expands to nothing.
    private static final Function<Identifier, Set<Identifier>> NO_TAGS = tag -> Set.of();

    private GooBaseValuesPreview() {
    }

    /**
     * @param json base_values.json text
     * @return each item's base value as the loader parses it, before any recipe derives
     * @throws IOException where the text cannot be read
     */
    public static Map<Identifier, GooValue> baseValues(String json) throws IOException {
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new LinkedHashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        GooValueLoader.parseBaseValuesFromStream(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), state);
        return state.baseValues;
    }

    /**
     * @param json base_values.json text
     * @return a registry holding its base values as the game loads them, before any recipe derives, with no
     *         item tag known
     */
    public static GooValueRegistry registry(String json) {
        return registry(json, NO_TAGS);
    }

    /**
     * @param json        base_values.json text
     * @param tagResolver resolves an item tag to its member ids, standing in for the game's registry
     * @return a registry holding its base values as the game loads them, before any recipe derives
     */
    public static GooValueRegistry registry(String json, Function<Identifier, Set<Identifier>> tagResolver) {
        GooValueRegistry registry = new GooValueRegistry();
        registry.loadBaseValuesFromText(json, tagResolver);
        return registry;
    }

    /**
     * Each item's value as the game holds it before any recipe derives: the base values with the
     * _post_conversions applied over them, as the type-source checks read a draft
     * (decision type-source-checks-are-rules-that-gate-promote).
     *
     * @param json        base_values.json text
     * @param tagResolver resolves an item tag to its member ids, standing in for the game's registry
     * @return each item's value, base and post-converted
     */
    public static Map<Identifier, GooValue> postConverted(String json,
                                                          Function<Identifier, Set<Identifier>> tagResolver) {
        GooValueRegistry registry = registry(json, tagResolver);
        registry.publishBaseAsEffective();
        return registry.table().getEffectiveValues();
    }

    /**
     * base_values.json as the loader reads it.
     *
     * @param baseValues each item's base value
     * @param denied     the items denied a value
     * @param restricted the items restricted from melting
     * @param warnings   the validator's warnings, each led by the entry it names
     * @param roots      the clique roots _roots names, in file order
     */
    public record Loaded(Map<Identifier, GooValue> baseValues, Set<Identifier> denied, Set<Identifier> restricted,
                         List<String> warnings, List<Identifier> roots) {
    }

    /**
     * @param json base_values.json text
     * @return its base values, denials, restrictions and the validator's warnings
     */
    public static Loaded load(String json) {
        return load(json, NO_TAGS);
    }

    /**
     * @param json        base_values.json text
     * @param tagResolver resolves an item tag to its member ids, standing in for the game's registry
     * @return its base values, denials, restrictions and the validator's warnings
     */
    public static Loaded load(String json, Function<Identifier, Set<Identifier>> tagResolver) {
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new LinkedHashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        GooValueLoader.applyMergedLayers(List.of(JsonParser.parseString(json).getAsJsonObject()), state, tagResolver);
        List<String> warnings = new ArrayList<>();
        GooValueValidator.validateJson(JsonParser.parseString(json).getAsJsonObject(), warnings);
        return new Loaded(state.baseValues, state.deniedItems, state.restrictedItems, warnings,
                List.copyOf(state.rootItems));
    }

    /**
     * Every item's value once the recipes derive, as the server derives it at load.
     *
     * @param effective each item's value, base and derived
     * @param derived   the items a recipe valued
     * @param recipes   the recipes that set a value, the winning one per output
     * @param registry  the registry the derivation ran in, which every further read of it takes
     */
    public record Derived(Map<Identifier, GooValue> effective, Set<Identifier> derived, List<RecipeInput> recipes,
                          GooValueRegistry registry) {
    }

    /**
     * @param json    base_values.json text
     * @param recipes the loadout's recipes
     * @return every item's value after derivation, under the default rule that the lower total wins
     */
    public static Derived derive(String json, List<RecipeInput> recipes) {
        return derive(json, recipes, NO_TAGS);
    }

    /**
     * @param json        base_values.json text
     * @param recipes     the loadout's recipes
     * @param tagResolver resolves an item tag to its member ids, standing in for the game's registry
     * @return every item's value after derivation, under the default rule that the lower total wins
     */
    public static Derived derive(String json, List<RecipeInput> recipes,
                                 Function<Identifier, Set<Identifier>> tagResolver) {
        GooValueRegistry registry = registry(json, tagResolver);
        registry.deriveFromRecipeInputs(recipes, false);
        return new Derived(registry.table().getEffectiveValues(), registry.lastDerivedValues().keySet(),
                registry.valueSettingRecipes(), registry);
    }

    /**
     * The _conversions and _post_conversions blocks as the game's conversion parser reads them.
     *
     * @param pre  the _conversions block, applied before recipes derive
     * @param post the _post_conversions block, applied after
     */
    public record Conversions(GooConversion.ParsedConversions pre, GooConversion.ParsedConversions post) {
    }

    /**
     * @param json base_values.json text
     * @return both conversion blocks as the loader parses them, each empty where the file holds none
     */
    public static Conversions conversions(String json) {
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new LinkedHashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        GooValueLoader.applyMergedLayers(List.of(JsonParser.parseString(json).getAsJsonObject()), state, NO_TAGS);
        return new Conversions(orEmpty(state.preConversions), orEmpty(state.postConversions));
    }

    private static GooConversion.ParsedConversions orEmpty(GooConversion.@Nullable ParsedConversions parsed) {
        return parsed != null ? parsed : GooConversion.parseBlock(Map.of());
    }

    /**
     * @param json    base_values.json text
     * @param recipes the loadout's recipes
     * @return a registry holding the text's values derived over the recipes, as the server holds it after load
     */
    public static GooValueRegistry derivedRegistry(String json, List<RecipeInput> recipes) {
        GooValueRegistry registry = registry(json);
        registry.deriveFromRecipeInputs(recipes, false);
        return registry;
    }

    /**
     * @param members a _groups entry's array
     * @return the item ids the loader reads from it
     */
    public static Set<Identifier> groupMembers(JsonArray members) {
        return GooGroupParser.parseGroupMembers(members);
    }
}

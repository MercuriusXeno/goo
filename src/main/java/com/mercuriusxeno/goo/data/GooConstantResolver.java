package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.type.GooTypes;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a _constants block through the game's own constant parser and validator, so a tool authoring
 * base_values.json accepts and refuses exactly what the game does (decision workbench-authors-base-values-whole).
 */
public final class GooConstantResolver {
    private static final String CONSTANTS = "_constants";
    private static final String CONTEXT_PREFIX = "_constants.";
    private static final String CONTEXT_END = ":";
    private static final String TYPE_SEPARATOR = ".";
    private static final String WARNING_SEPARATOR = "; ";
    private static final String UNPARSED = "not parsed: ";

    private GooConstantResolver() {
    }

    /**
     * One constant as the game resolves it.
     *
     * @param name    the constant's name
     * @param tree    whether it resolves to a per-type tree rather than one number
     * @param scalar  the number a scalar constant resolves to, or null for a tree
     * @param perType each goo type's amount in a tree constant, by type id
     * @param error   the validator's warnings for the constant, or null where it has none
     */
    public record ResolvedConstant(String name, boolean tree, @Nullable Integer scalar, Map<String, String> perType,
                                   @Nullable String error) {
    }

    /**
     * Resolves each constant in order, each reading only the constants above it, as the game loads them.
     *
     * @param constants the _constants block
     * @return each constant resolved, in the block's order
     */
    public static List<ResolvedConstant> resolve(JsonObject constants) {
        List<String> warnings = new ArrayList<>();
        JsonObject root = new JsonObject();
        root.add(CONSTANTS, constants);
        GooValueValidator.validateJson(root, warnings);
        validateTreeFields(constants, warnings);
        GooValueLoader.ParseState state = new GooValueLoader.ParseState(new HashMap<>(), new HashMap<>(),
                new HashSet<>(), new HashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>());
        List<ResolvedConstant> resolved = new ArrayList<>();
        for (Map.Entry<String, JsonElement> constant : constants.entrySet()) {
            resolved.add(resolveOne(constant.getKey(), constant.getValue(), state, warnings));
        }
        return resolved;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    // A malformed expression throws from deep in the evaluator; the tool shows it beside the constant.
    private static ResolvedConstant resolveOne(String name, JsonElement value, GooValueLoader.ParseState state,
                                               List<String> warnings) {
        List<String> own = new ArrayList<>(warningsFor(name, warnings));
        try {
            GooConstantParser.parseOneConstant(name, value, state);
        } catch (RuntimeException e) {
            own.add(UNPARSED + e.getMessage());
        }
        String error = own.isEmpty() ? null : String.join(WARNING_SEPARATOR, own);
        GooValue tree = state.treeConstants.get(name);
        if (tree != null) {
            Map<String, String> perType = new LinkedHashMap<>();
            tree.exactAll().forEach((type, amount) -> perType.put(GooTypes.id(type), amount.toString()));
            return new ResolvedConstant(name, true, null, perType, error);
        }
        return new ResolvedConstant(name, false, state.constants.get(name), Map.of(), error);
    }

    private static List<String> warningsFor(String name, List<String> warnings) {
        String exact = CONTEXT_PREFIX + name + CONTEXT_END;
        String typed = CONTEXT_PREFIX + name + TYPE_SEPARATOR;
        return warnings.stream().filter(warning -> warning.startsWith(exact) || warning.startsWith(typed)).toList();
    }

    /**
     * The game's validator reads a scalar constant's expression; a tree constant's per-type expressions
     * take the same token check here, against the constants above it.
     *
     * @param constants the _constants block
     * @param warnings  the warnings so far, added to
     */
    private static void validateTreeFields(JsonObject constants, List<String> warnings) {
        Set<String> known = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> constant : constants.entrySet()) {
            if (constant.getValue().isJsonObject()) {
                for (Map.Entry<String, JsonElement> field : constant.getValue().getAsJsonObject().entrySet()) {
                    if (field.getValue().isJsonPrimitive() && field.getValue().getAsJsonPrimitive().isString()) {
                        GooValueTokenValidator.validateExprTokens(field.getValue().getAsString(), known,
                                new LinkedHashSet<>(), CONTEXT_PREFIX + constant.getKey() + TYPE_SEPARATOR
                                        + field.getKey(), warnings);
                    }
                }
            }
            known.add(constant.getKey());
        }
    }
}

package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import java.util.Map;
import java.util.Set;

/**
 * Parses the _constants block from base_values.json into scalar and tree constants.
 * Scalar constants resolve to a single exact amount, a division keeping its fraction; tree constants resolve to a
 * full GooValue (multi-type map) (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
public final class GooConstantParser {

    private static final String CONSTANTS_SUFFIX = "_constants";
    private static final String LOG_LOADED_CONSTANTS = "Loaded {} constants ({} scalar, {} tree)";
    private static final String LOG_UNRESOLVED_ITEM = "Constant ${} names {}, which holds no value, so it loads no value";
    private static final String LOG_CYCLE = "Prefabs {}";

    private GooConstantParser() {
    }

    /**
     * Parses the _constants object from the JSON root, if present, each prefab after every prefab it reads.
     *
     * @param json  the root JSON object containing the _constants key
     * @param state mutable parsing state
     */
    static void parseConstants(JsonObject json, GooValueLoader.ParseState state) {
        state.constants.clear();
        state.treeConstants.clear();
        if (!json.has(CONSTANTS_SUFFIX)) {
            return;
        }
        JsonObject obj = json.getAsJsonObject(CONSTANTS_SUFFIX);
        PrefabDependencyGraph graph = PrefabDependencyGraph.of(obj);
        Set<String> cycled = graph.cycled();
        for (String name : graph.order()) {
            if (!cycled.contains(name)) {
                parseOneConstant(name, obj.get(name), state);
            }
        }
        if (Goo.LOGGER.isWarnEnabled()) {
            graph.cycles().forEach(cycle -> Goo.LOGGER.warn(LOG_CYCLE, PrefabDependencyGraph.cycleError(cycle)));
        }
        logConstantCounts(state);
    }

    /**
     * Logs the number of parsed scalar and tree constants at INFO level.
     *
     * @param state the mutable parsing state containing scalar and tree constant maps
     */
    private static void logConstantCounts(GooValueLoader.ParseState state) {
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED_CONSTANTS,
                    state.constants.size() + state.treeConstants.size(),
                    state.constants.size(), state.treeConstants.size());
        }
    }

    /**
     * Parses a single constant entry, dispatching to tree or scalar handling.
     *
     * @param name  the constant name
     * @param value the JSON value (object for tree, string for scalar/expression)
     * @param state mutable parsing state
     */
    static void parseOneConstant(String name, JsonElement value, GooValueLoader.ParseState state) {
        try {
            parseConstantValue(name, value, state);
        } catch (UnresolvedItemReference unresolved) {
            Goo.LOGGER.warn(LOG_UNRESOLVED_ITEM, name, unresolved.item());
        }
    }

    private static void parseConstantValue(String name, JsonElement value, GooValueLoader.ParseState state) {
        if (value.isJsonObject()) {
            state.treeConstants.put(name,
                    GooValueJsonFormat.parseGooValue(value.getAsJsonObject(),
                            state.constants, null, state.treeConstants));
        } else {
            parseScalarConstant(name, value, state);
        }
    }

    /**
     * Parses a scalar constant. Tries tree evaluation first for expressions
     * referencing tree constants (e.g. "9 $metal_nugget"), falling back to scalar.
     *
     * @param name  the constant name
     * @param value the JSON value to resolve
     * @param state mutable parsing state
     */
    static void parseScalarConstant(String name, JsonElement value, GooValueLoader.ParseState state) {
        String expr = value.getAsString().trim();
        if (referencesTreeConstant(expr, state.treeConstants)) {
            GooValue tree = GooValueExpression.evaluate(expr, Map.of(),
                    state.constants, state.treeConstants);
            if (!tree.isEmpty()) {
                state.treeConstants.put(name, tree);
                return;
            }
        }
        state.constants.put(name, GooValueJsonFormat.resolveConstantValue(value, state.constants));
    }

    /**
     * True if the expression string contains a $ref that's a known tree constant.
     *
     * @param expr          the expression to scan for tree constant references
     * @param treeConstants the known tree constants
     * @return true if any $ref resolves to a tree constant
     */
    static boolean referencesTreeConstant(String expr, Map<String, GooValue> treeConstants) {
        int i = expr.indexOf('$');
        while (i >= 0 && i < expr.length() - 1) {
            int end = scanIdentifier(expr, i + 1);
            if (end > i + 1 && treeConstants.containsKey(expr.substring(i + 1, end))) {
                return true;
            }
            i = expr.indexOf('$', end);
        }
        return false;
    }

    /**
     * Scans forward from {@code start} while characters are word-like (letter, digit, or '_'); the values manager
     * reads its $name links through this scan, so a link names what the loader resolves
     * (decision references-link-to-their-variable-and-hover-a-card).
     *
     * @param expr  the string to scan
     * @param start the starting index
     * @return the index of the first non-word character (or string length)
     */
    public static int scanIdentifier(String expr, int start) {
        int end = start;
        while (end < expr.length() && (Character.isLetterOrDigit(expr.charAt(end)) || expr.charAt(end) == '_')) {
            end++;
        }
        return end;
    }
}

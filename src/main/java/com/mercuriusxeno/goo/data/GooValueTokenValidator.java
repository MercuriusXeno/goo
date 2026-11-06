package com.mercuriusxeno.goo.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates expression tokens within goo value definitions: checks for forgotten
 * $ prefixes and unknown constants; an item reference waits for its item rather than warning. Extracted from
 * {@link GooValueValidator} to keep per-class method counts manageable.
 */
final class GooValueTokenValidator {

    /** Operator characters skipped during expression validation. */
    private static final String OPERATOR_CHARS = "+-*/()";
    /** Dollar-sign prefix for constant references. */
    private static final String DOLLAR_PREFIX = "$";
    /** Colon character used in namespaced identifiers. */
    private static final String COLON = ":";
    /** Default namespace for unqualified item names. */
    private static final String DEFAULT_NS_PREFIX = "minecraft:";

    /** Warning suffix: unknown constant prefix. */
    private static final String WARN_UNKNOWN_CONST_DOLLAR = ": unknown constant $";
    /** Warning lead: the referent and the goo type it lacks follow. */
    private static final String WARN_LACKED_TYPE = ": ";

    /** Warning: missing $ prefix pattern start. */
    private static final String WARN_MISSING_DOLLAR_PREFIX = ": '";
    /** Warning: missing $ prefix pattern middle. */
    private static final String WARN_MISSING_DOLLAR_MID = "' looks like a constant missing its $ prefix (should be $";
    /** Warning: missing $ prefix pattern end. */
    private static final String WARN_MISSING_DOLLAR_SUFFIX = ")";


    private GooValueTokenValidator() {}

    /**
     * Checks expression tokens for forgotten $ prefixes, unknown constants, and out-of-order items.
     *
     * @param expr the expression string to validate
     * @param knownConstants known constant names
     * @param knownItems known item IDs (defined before this entry)
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateExprTokens(String expr, Set<String> knownConstants,
                                   Set<String> knownItems, String context,
                                   List<String> warnings) {
        validateExprTokens(expr, new KnownPrefabs(knownConstants, Map.of()), knownItems, context, warnings);
    }

    /**
     * Checks expression tokens for forgotten $ prefixes, unknown constants, out-of-order items, and a $name.type
     * reading a type an object-form prefab holds no key for.
     *
     * @param expr the expression string to validate
     * @param prefabs the prefabs the block names, with each object-form prefab's goo types
     * @param knownItems known item IDs (defined before this entry)
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateExprTokens(String expr, KnownPrefabs prefabs, Set<String> knownItems, String context,
                                   List<String> warnings) {
        for (String token : ExpressionTokenizer.tokenize(expr)) {
            if (!isOperatorToken(token)) {
                classifyAndValidateToken(token, prefabs, knownItems, context, warnings);
            }
        }
    }

    /**
     * The prefabs a _constants block names, and the goo types each object-form prefab holds, its JSON keys; a prefab
     * in string form holds the types its expression resolves to, which only the evaluator knows.
     *
     * @param names every prefab name the block holds
     * @param heldTypes each object-form prefab's goo type ids, by prefab name
     */
    record KnownPrefabs(Set<String> names, Map<String, Set<String>> heldTypes) {
    }

    /**
     * Dispatches a non-operator token to the appropriate validator by its prefix. An item token, namespaced or bare,
     * draws no warning wherever it stands: the entry waits for the item, a line below or the recipes valuing it, and
     * the load names an item nothing values (decision diagnose-and-correct-item-reference-unrecognized-token).
     *
     * @param token the token to classify and validate
     * @param prefabs the prefabs the block names, with each object-form prefab's goo types
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void classifyAndValidateToken(String token, KnownPrefabs prefabs,
                                         Set<String> knownItems, String context,
                                         List<String> warnings) {
        if (token.startsWith(DOLLAR_PREFIX)) {
            validateConstantToken(token, prefabs, context, warnings);
        } else if (!token.contains(COLON) && !Character.isDigit(token.charAt(0))) {
            validateBareWordToken(token, prefabs.names(), knownItems, context, warnings);
        }
    }

    /**
     * Returns true if the token is a single-character operator or parenthesis.
     *
     * @param token the token to check
     * @return true if operator/paren
     */
    static boolean isOperatorToken(String token) {
        return token.length() == 1 && OPERATOR_CHARS.contains(token);
    }

    /**
     * Validates a $-prefixed constant token, stripping dot-notation suffixes for lookup, and warns where the suffix
     * reads a goo type the object-form prefab holds no key for
     * (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
     *
     * @param token the constant token (e.g. "$food.jelly")
     * @param prefabs the prefabs the block names, with each object-form prefab's goo types
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateConstantToken(String token, KnownPrefabs prefabs,
                                      String context, List<String> warnings) {
        String name = token.substring(1);
        int dot = name.indexOf('.');
        String baseName = dot >= 0 ? name.substring(0, dot) : name;
        if (!prefabs.names().contains(baseName)) {
            warnings.add(context + WARN_UNKNOWN_CONST_DOLLAR + name);
            return;
        }
        Set<String> held = prefabs.heldTypes().get(baseName);
        String type = name.substring(name.lastIndexOf('.') + 1);
        if (dot >= 0 && held != null && !held.contains(type)) {
            warnings.add(context + WARN_LACKED_TYPE + ReferentLacksType.message(baseName, type));
        }
    }

    /**
     * Warns on a bare word naming a constant and no item above, a constant missing its $ prefix.
     *
     * @param token the bare word token
     * @param knownConstants known constant names
     * @param knownItems known item IDs defined before this entry
     * @param context the item key for error messages
     * @param warnings accumulator for validation warnings
     */
    static void validateBareWordToken(String token, Set<String> knownConstants,
                                      Set<String> knownItems, String context,
                                      List<String> warnings) {
        String bareItem = stripTypeSuffix(token);
        boolean knownItem = knownItems.contains(DEFAULT_NS_PREFIX + bareItem) || knownItems.contains(bareItem);
        if (!knownItem && knownConstants.contains(token)) {
            warnings.add(context + WARN_MISSING_DOLLAR_PREFIX + token + WARN_MISSING_DOLLAR_MID + token
                    + WARN_MISSING_DOLLAR_SUFFIX);
        }
    }

    /**
     * Strips a trailing .type suffix (e.g. "iron_ingot.metal" becomes "iron_ingot").
     *
     * @param token the bare word token to strip
     * @return the token without its type suffix, or unchanged if none
     */
    static String stripTypeSuffix(String token) {
        int dotIdx = token.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < token.length() - 1) {
            return token.substring(0, dotIdx);
        }
        return token;
    }
}
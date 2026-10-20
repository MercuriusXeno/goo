package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Correlates each constant and each referenced item in the merged base_values JSON
 * with every entry whose raw expression consumes it, for the audit's FORMULAS section.
 * decision audit-dump-from-a-gametest
 */
public final class GooFormulaReferences {

    private static final String CONSTANTS = "_constants";
    private static final String CONSTANT_PREFIX = "$";
    private static final String INTERNAL_PREFIX = "_";
    private static final String TAG_PREFIX = "#";
    private static final String DENIED = "denied";

    private GooFormulaReferences() {
    }

    /**
     * Every constant answers, consumed or not; an item answers once an expression reads it.
     *
     * @param merged the merged base_values JSON
     * @return each operand, a constant as $name or an item as its full id, to the entries consuming it
     */
    public static SortedMap<String, SortedSet<String>> consumersByOperand(JsonObject merged) {
        SortedMap<String, SortedSet<String>> consumers = new TreeMap<>();
        if (merged.has(CONSTANTS) && merged.get(CONSTANTS).isJsonObject()) {
            for (Map.Entry<String, JsonElement> constant : merged.getAsJsonObject(CONSTANTS).entrySet()) {
                String name = CONSTANT_PREFIX + constant.getKey();
                consumers.computeIfAbsent(name, key -> new TreeSet<>());
                recordOperands(name, constant.getValue(), consumers);
            }
        }
        for (Map.Entry<String, JsonElement> entry : merged.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(INTERNAL_PREFIX)) {
                recordOperands(entryName(key), entry.getValue(), consumers);
            }
        }
        return consumers;
    }

    private static void recordOperands(String consumer, JsonElement value,
                                       SortedMap<String, SortedSet<String>> consumers) {
        for (String expression : expressionsOf(value)) {
            for (String token : ExpressionTokenizer.tokenize(expression)) {
                String operand = operandOf(token);
                if (operand != null) {
                    consumers.computeIfAbsent(operand, key -> new TreeSet<>()).add(consumer);
                }
            }
        }
    }

    /**
     * An entry is one expression string, or an object of per-type expression strings;
     * a number and the word denied read nothing.
     *
     * @param value an entry's JSON value
     * @return the expression strings it holds
     */
    private static List<String> expressionsOf(JsonElement value) {
        List<String> expressions = new ArrayList<>();
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
                expressions.addAll(expressionsOf(field.getValue()));
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !DENIED.equals(value.getAsString())) {
            expressions.add(value.getAsString());
        }
        return expressions;
    }

    /**
     * @param token an expression token
     * @return the constant or item it reads, or null for a number, operator or parenthesis
     */
    private static @Nullable String operandOf(String token) {
        if (token.startsWith(CONSTANT_PREFIX)) {
            int dot = token.indexOf('.');
            return dot < 0 ? token : token.substring(0, dot);
        }
        return Character.isLetter(token.charAt(0)) ? itemOf(token) : null;
    }

    /**
     * @param token an item token, bare, namespaced or in dot form such as coal.blaze
     * @return the item's full id, or null for text no identifier accepts
     */
    private static @Nullable String itemOf(String token) {
        int colon = token.indexOf(':');
        int dot = token.lastIndexOf('.');
        String item = dot > 0 && dot > colon ? token.substring(0, dot) : token;
        Identifier id = Identifier.tryParse(item);
        return id == null ? null : id.toString();
    }

    private static String entryName(String key) {
        if (key.startsWith(TAG_PREFIX)) {
            return key;
        }
        Identifier id = Identifier.tryParse(key);
        return id == null ? key : id.toString();
    }
}

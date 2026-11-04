package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The tooltip every prefab reference the manager draws shows on hover: in an Items value, a Groups expression, a
 * Prefabs read view and a Mutators row, one head line "$name = expression (reduced value)" read from the draft and
 * the last evaluate reply, so it refreshes as the evaluation does (decisions every-prefab-reference-hovers-its-value,
 * prefab-tooltip-reads-name-equals-expression-then-reduced-value).
 */
final class PrefabHovers {
    // A hover key no prefab name, item id or hint spells.
    private static final String HOVER_PREFIX = "goo-prefab:";
    private static final String EQUALS = " = ";
    private static final String TYPE_SEPARATOR = ", ";
    private static final String OPEN = " (";
    private static final String CLOSE = ")";
    private static final String NO_TEXT = "";

    private PrefabHovers() {
    }

    /**
     * @param name a prefab's name
     * @return the hover key whose tooltip shows the prefab's value
     */
    static String hoverKey(String name) {
        return HOVER_PREFIX + name;
    }

    /**
     * @param variables the Prefabs tab's state, holding the draft's expressions and the last evaluate reply
     * @param hoverKey  what the hovered area names
     * @return a prefab's head line, then its error where it holds one; the bare name where the area names no
     *     prefab, as a mutator's does; empty where the area is no reference's
     */
    static Optional<List<TooltipLine>> tooltipFor(VariablesTab variables, String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String name = hoverKey.substring(HOVER_PREFIX.length());
        Optional<String> expression = variables.expressionOf(name);
        if (expression.isEmpty()) {
            return Optional.of(List.of(line(name)));
        }
        Optional<VariablesTab.PrefabValue> value = variables.prefabValue(name);
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(line(PrefabNames.marked(name) + EQUALS + body(expression.get(), value)));
        value.map(VariablesTab.PrefabValue::error).filter(error -> !error.isEmpty())
                .ifPresent(error -> lines.add(line(error)));
        return Optional.of(lines);
    }

    // A tree reads each type = its expression with that type's amount after it; a scalar its expression and value.
    private static String body(String text, Optional<VariablesTab.PrefabValue> value) {
        Map<String, String> tree = value.map(VariablesTab.PrefabValue::tree).orElse(Map.of());
        Optional<Map<String, String>> types = typeExpressions(text);
        if (types.isPresent()) {
            // Decision value-lists-order-highest-first-ties-by-type-key: each type in its resolved amount's order.
            Map<String, String> amounts = new LinkedHashMap<>();
            types.get().keySet().forEach(type -> amounts.put(type, tree.getOrDefault(type, NO_TEXT)));
            Map<String, String> expressions = types.get();
            return AmountOrder.highestFirst(amounts).entrySet().stream().map(type -> type.getKey() + EQUALS
                    + reduced(expressions.get(type.getKey()), type.getValue()))
                    .collect(Collectors.joining(TYPE_SEPARATOR));
        }
        return reduced(ManagerDraft.shownText(text), value.map(VariablesTab.PrefabValue::scalar).orElse(NO_TEXT));
    }

    // Decision prefab-tooltip-reads-name-equals-expression-then-reduced-value: the reduced value in its own
    // parentheses after the expression, where it reads otherwise than the expression, and no (type: scalar).
    private static String reduced(String expression, String value) {
        return value.isEmpty() || value.equals(expression.trim()) ? expression : expression + OPEN + value + CLOSE;
    }

    // A tree's text is an object of goo type to expression; anything else is a scalar's.
    private static Optional<Map<String, String>> typeExpressions(String text) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            return Optional.empty();
        }
        if (!parsed.isJsonObject()) {
            return Optional.empty();
        }
        Map<String, String> types = new LinkedHashMap<>();
        parsed.getAsJsonObject().entrySet().forEach(type -> types.put(type.getKey(),
                type.getValue().isJsonPrimitive() ? type.getValue().getAsString() : type.getValue().toString()));
        return Optional.of(types);
    }

    private static TooltipLine line(String text) {
        return new TooltipLine.Text(Component.literal(text));
    }
}

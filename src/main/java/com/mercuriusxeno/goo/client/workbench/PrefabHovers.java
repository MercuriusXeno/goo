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
import java.util.function.Function;

/**
 * The tooltip every prefab reference the manager draws shows on hover: in an Items value, a Groups expression, a
 * Prefabs read view and a Mutators row. It reads the prefab's recipe, then its reduction in parentheses where that
 * reads otherwise, each a grid of one cell per goo type, {type}: its terms then {type}: its amount, read from the
 * draft and the last evaluate reply so it refreshes as the evaluation does (decisions
 * every-prefab-reference-hovers-its-value, read-view-reads-type-equals-possessive-terms,
 * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
 */
final class PrefabHovers {
    // A hover key no prefab name, item id or hint spells.
    private static final String HOVER_PREFIX = "goo-prefab:";
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
     * @return a prefab's recipe, its reduction where it reads otherwise, then its error where it holds one; the
     *     bare name where the area names no prefab, as a mutator's does; empty where the area is no reference's
     */
    static Optional<List<TooltipLine>> tooltipFor(VariablesTab variables, String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String name = hoverKey.substring(HOVER_PREFIX.length());
        Optional<String> expression = variables.expressionOf(name);
        if (expression.isEmpty()) {
            return Optional.of(List.of(new TooltipLine.Text(Component.literal(name))));
        }
        Optional<VariablesTab.PrefabValue> value = variables.prefabValue(name);
        List<TooltipLine> lines = new ArrayList<>(body(expression.get(), value));
        value.map(VariablesTab.PrefabValue::error).filter(error -> !error.isEmpty())
                .ifPresent(error -> lines.add(new TooltipLine.Text(Component.literal(error))));
        return Optional.of(lines);
    }

    private static List<TooltipLine> body(String text, Optional<VariablesTab.PrefabValue> value) {
        Optional<Map<String, String>> types = typeExpressions(text);
        if (types.isPresent()) {
            return typedBody(types.get(), value.map(VariablesTab.PrefabValue::tree).orElse(Map.of()));
        }
        String shown = ManagerDraft.shownText(text);
        String scalar = value.map(VariablesTab.PrefabValue::scalar).orElse(NO_TEXT);
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine.RunsGrid(List.of(termRuns(shown))));
        if (!scalar.isEmpty() && !scalar.equals(shown.trim())) {
            // Decision one-amount-grid-draws-every-amount-list: the reduction is the one amount grid, in its parens.
            lines.add(GooAmountGrid.reducedScalar(scalar));
        }
        return lines;
    }

    // Decision value-lists-order-highest-first-ties-by-type-key: both grids read each type in its resolved amount's
    // order. The reduction stands only where some amount reads otherwise than its expression.
    private static List<TooltipLine> typedBody(Map<String, String> expressions, Map<String, String> tree) {
        Map<String, String> amounts = new LinkedHashMap<>();
        expressions.keySet().forEach(type -> amounts.put(type, tree.getOrDefault(type, NO_TEXT)));
        List<String> order = new ArrayList<>(AmountOrder.highestFirst(amounts).keySet());
        List<List<ExpressionLines.Run>> recipe = new ArrayList<>();
        order.forEach(type -> recipe.add(typed(type, termRuns(expressions.get(type)))));
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine.RunsGrid(recipe));
        boolean reduces = order.stream().anyMatch(type -> !amounts.get(type).isEmpty()
                && !amounts.get(type).equals(expressions.get(type).trim()));
        if (reduces) {
            // Decision one-amount-grid-draws-every-amount-list: each type's icon then its amount, in the grid's parens.
            Map<String, String> reduced = new LinkedHashMap<>();
            order.forEach(type -> reduced.put(type, amounts.get(type)));
            lines.add(GooAmountGrid.reducedLine(reduced));
        }
        return lines;
    }

    // {type}: then the terms, as the read view writes a type row.
    private static List<ExpressionLines.Run> typed(String type, List<ExpressionLines.Run> terms) {
        List<ExpressionLines.Run> cell = new ArrayList<>();
        cell.add(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, NO_TEXT, type));
        cell.add(operator(ExpressionLines.COLON));
        cell.addAll(terms);
        return cell;
    }

    // The terms one expression draws as, a member read as its prefab's own; text the terms cannot read draws red.
    private static List<ExpressionLines.Run> termRuns(String expression) {
        return ExpressionLines.shownRuns(expression, Function.identity());
    }

    private static ExpressionLines.Run operator(String text) {
        return new ExpressionLines.Run(ExpressionLines.Kind.OPERATOR, text, null);
    }

    // A typed prefab's text is an object of goo type to expression; anything else is a scalar's.
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
}

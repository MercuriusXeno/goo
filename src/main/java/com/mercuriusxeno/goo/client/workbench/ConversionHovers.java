package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tooltip a Conversions row's controls show on hover: a convert reads its recipe as icons and numbers, an
 * additive its prefab's formula, and a stack the mutator it applies with every amount multiplied by its count, so it
 * reads like that mutator times the number of stacks (decisions conversion-controls-hover-their-formula,
 * every-linkable-thing-hovers-its-preview-value).
 */
final class ConversionHovers {
    // A hover key no prefab name, item id or hint spells; a newline parts the section from the entry's key.
    private static final String HOVER_PREFIX = "goo-conversion:";
    private static final char KEY_SEPARATOR = '\n';
    private static final String NOTHING_TARGET = "0";
    private static final String DIVIDE = "/";
    private static final String ARROW = "=>";
    private static final String TIMES_ZERO = "x0";
    private static final String TIMES = " x ";
    private static final String NO_TEXT = "";
    // A stack that applies a stack multiplies through it; past this depth the chain loops and hovers nothing.
    private static final int MAX_STACK_DEPTH = 8;

    private ConversionHovers() {
    }

    /**
     * @param section the entry's block
     * @param key     the entry's key
     * @return the hover key whose tooltip shows the entry's formula
     */
    static String hoverKey(String section, String key) {
        return HOVER_PREFIX + section + KEY_SEPARATOR + key;
    }

    /**
     * @param processes the Conversions tab's state
     * @param variables the Prefabs tab's state, which an additive's prefab reads
     * @param hoverKey  what the hovered area names
     * @return the entry's formula lines; empty where the area is no conversion's or its formula reads nothing
     */
    static Optional<List<TooltipLine>> tooltipFor(ProcessesTab processes, VariablesTab variables, String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String entry = hoverKey.substring(HOVER_PREFIX.length());
        int separator = entry.indexOf(KEY_SEPARATOR);
        if (separator < 0) {
            return Optional.empty();
        }
        String section = entry.substring(0, separator);
        return linesOf(new Lookup(processes, variables, section), processes.formOf(section,
                entry.substring(separator + 1)), 1, 0);
    }

    /**
     * What a formula reads its parts from.
     *
     * @param processes the Conversions tab's state
     * @param variables the Prefabs tab's state
     * @param section   the entry's block, where a stack's mutator stands
     */
    private record Lookup(ProcessesTab processes, VariablesTab variables, String section) {
    }

    private static Optional<List<TooltipLine>> linesOf(Lookup lookup, Processes.Form form, long count, int depth) {
        return switch (form) {
            case Processes.Formula formula -> Optional.of(List.of(new TooltipLine.Runs(formulaRuns(formula, count))));
            case Processes.Additive additive -> additiveLines(lookup.variables(), additive.constant(), count);
            case Processes.Stack stack -> stackLines(lookup, stack, count, depth);
            case Processes.Assignment _, Processes.Unread _ -> Optional.empty();
        };
    }

    // The mutator a stack applies, its amounts multiplied by the stack's count.
    private static Optional<List<TooltipLine>> stackLines(Lookup lookup, Processes.Stack stack, long count,
                                                          int depth) {
        if (depth >= MAX_STACK_DEPTH) {
            return Optional.empty();
        }
        return linesOf(lookup, lookup.processes().formOf(lookup.section(), stack.formula()), count * stack.count(),
                depth + 1);
    }

    // {count if > 1} [source] {/ div if > 1} => x0, or {mult times count if > 1} [target] {/ div if > 1}.
    private static List<ExpressionLines.Run> formulaRuns(Processes.Formula formula, long count) {
        List<ExpressionLines.Run> runs = new ArrayList<>();
        addSide(runs, count, formula.source(), formula.sourceDivisor());
        runs.add(symbol(ARROW));
        if (NOTHING_TARGET.equals(formula.target()) || formula.multiplier() == 0) {
            runs.add(number(TIMES_ZERO));
        } else {
            addSide(runs, count * formula.multiplier(), formula.target(), formula.divisor());
        }
        return runs;
    }

    private static void addSide(List<ExpressionLines.Run> runs, long multiplier, String type, int divisor) {
        if (multiplier > 1) {
            runs.add(number(String.valueOf(multiplier)));
        }
        runs.add(new ExpressionLines.Run(ExpressionLines.Kind.TYPE, NO_TEXT, type));
        if (divisor > 1) {
            runs.add(symbol(DIVIDE));
            runs.add(number(String.valueOf(divisor)));
        }
    }

    // An additive once reads its prefab's own tooltip; stacked, each of the prefab's amounts times the count.
    private static Optional<List<TooltipLine>> additiveLines(VariablesTab variables, String prefab, long count) {
        if (count == 1) {
            return PrefabHovers.tooltipFor(variables, PrefabHovers.hoverKey(prefab));
        }
        Map<String, String> tree = variables.prefabValue(prefab).map(VariablesTab.PrefabValue::tree).orElse(Map.of());
        List<TooltipLine> lines = new ArrayList<>();
        tree.forEach((type, amount) -> lines.add(new TooltipLine.GooAmount(type, times(amount, count))));
        return lines.isEmpty() ? PrefabHovers.tooltipFor(variables, PrefabHovers.hoverKey(prefab))
                : Optional.of(lines);
    }

    // An amount as the server writes it, a whole number or a reduced n/d, times a count; any other text reads as
    // it stands after the count.
    static String times(String amount, long count) {
        int divider = amount.indexOf(DIVIDE);
        try {
            long numerator = Long.parseLong((divider < 0 ? amount : amount.substring(0, divider)).trim()) * count;
            long denominator = divider < 0 ? 1 : Long.parseLong(amount.substring(divider + 1).trim());
            long common = greatestCommonDivisor(numerator, denominator);
            long top = numerator / common;
            long bottom = denominator / common;
            return bottom == 1 ? String.valueOf(top) : top + DIVIDE + bottom;
        } catch (NumberFormatException e) {
            return count + TIMES + amount;
        }
    }

    private static long greatestCommonDivisor(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0) {
            long remainder = x % y;
            x = y;
            y = remainder;
        }
        return x == 0 ? 1 : x;
    }

    private static ExpressionLines.Run number(String digits) {
        return new ExpressionLines.Run(ExpressionLines.Kind.NUMBER, digits, null);
    }

    private static ExpressionLines.Run symbol(String symbol) {
        return new ExpressionLines.Run(ExpressionLines.Kind.OPERATOR, symbol, null);
    }
}

package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionFlows;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The audit's FORMULAS and REACTIONS sections: which entries consume each constant
 * and item, and which reactions yield and spend each fluid.
 * decision audit-dump-from-a-gametest
 * decision goo-reactions-reviewed-against-the-new-types
 */
final class GooAuditFlows {

    private static final String HDR_FORMULAS_PREFIX = "FORMULAS (";
    private static final String HDR_FORMULAS_SUFFIX = " constants and referenced items)";
    private static final String MSG_NO_FORMULAS = "  No base values loaded.";
    private static final String HDR_REACTIONS_PREFIX = "REACTIONS (";
    private static final String HDR_REACTIONS_SUFFIX = " reactions under goo_reactions)";
    private static final String MSG_NO_REACTIONS = "  No reactions loaded.";
    private static final String CONSTANT_PREFIX = "$";
    private static final String RESOLVES_TO = " = ";
    private static final String CONSUMER_PREFIX = "    <- ";
    private static final String NO_CONSUMER = "    (no consumer)";
    private static final String UNRESOLVED = "unresolved";
    private static final String NO_VALUE = "no value";
    private static final String AMOUNT_SEPARATOR = " ";
    private static final String FLUID_JOIN = " + ";
    private static final String REACTION_ARROW = " -> ";
    private static final String RATE_OPEN = " (rate ";
    private static final String ID_SEPARATOR = ": ";
    private static final String BY_FLUID = "  By fluid:";
    private static final String YIELDED_BY = "      yielded by: ";
    private static final String SPENT_BY = "      spent by: ";
    private static final String NONE = "none";
    private static final String LIST_JOIN = ", ";
    private static final String FLUID_INDENT = "    ";

    private GooAuditFlows() {
    }

    /**
     * Appends FORMULAS: each constant and referenced item beside its resolved value and its consumers.
     *
     * @param values the server's goo value registry
     * @param report the report lines list
     */
    static void appendFormulasSection(GooValueRegistry values, List<String> report) {
        SortedMap<String, SortedSet<String>> consumers = values.formulaConsumers();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, SortedSet<String>> operand : consumers.entrySet()) {
            lines.add(GooAuditReport.INDENT + operand.getKey() + RESOLVES_TO + resolved(values, operand.getKey()));
            if (operand.getValue().isEmpty()) {
                lines.add(NO_CONSUMER);
            }
            operand.getValue().forEach(consumer -> lines.add(CONSUMER_PREFIX + consumer));
        }
        GooAuditReport.appendSectionBody(report, HDR_FORMULAS_PREFIX + consumers.size() + HDR_FORMULAS_SUFFIX,
                MSG_NO_FORMULAS, lines, line -> line);
    }

    private static String resolved(GooValueRegistry values, String operand) {
        if (operand.startsWith(CONSTANT_PREFIX)) {
            String constant = values.resolvedConstant(operand.substring(1));
            return constant == null ? UNRESOLVED : constant;
        }
        Identifier id = Identifier.tryParse(operand);
        GooValue value = id == null ? null : values.table().lookup(id);
        return value == null ? NO_VALUE : value.toString();
    }

    /**
     * Appends REACTIONS: every reaction's inputs and outputs, then under each fluid
     * the reactions yielding it and the reactions spending it.
     *
     * @param reactions the reactions the server loaded from goo_reactions
     * @param report    the report lines list
     */
    static void appendReactionsSection(List<GooReaction> reactions, List<String> report) {
        List<GooReaction> sorted = reactions.stream()
                .sorted((a, b) -> a.id().compareTo(b.id())).toList();
        List<String> lines = new ArrayList<>();
        sorted.forEach(reaction -> lines.add(GooAuditReport.INDENT + formatReaction(reaction)));
        if (!sorted.isEmpty()) {
            appendFluidIndex(sorted, lines);
        }
        GooAuditReport.appendSectionBody(report, HDR_REACTIONS_PREFIX + sorted.size() + HDR_REACTIONS_SUFFIX,
                MSG_NO_REACTIONS, lines, line -> line);
    }

    private static String formatReaction(GooReaction reaction) {
        return reaction.id() + ID_SEPARATOR + formatEntries(reaction.inputs()) + REACTION_ARROW
                + formatEntries(reaction.outputs()) + RATE_OPEN + reaction.rate() + GooAuditReport.MSG_CLOSE_PAREN;
    }

    private static String formatEntries(List<GooReaction.FluidEntry> entries) {
        return entries.stream()
                .map(entry -> entry.amount() + AMOUNT_SEPARATOR + GooReactionFlows.spell(entry.fluid()))
                .collect(Collectors.joining(FLUID_JOIN));
    }

    /**
     * Every bundled goo type answers, so a type no reaction yields reads as none;
     * a vanilla fluid or datapack type answers where a reaction names it.
     *
     * @param reactions the reactions, sorted by id
     * @param lines     the section lines to append to
     */
    private static void appendFluidIndex(List<GooReaction> reactions, List<String> lines) {
        GooReactionFlows flows = GooReactionFlows.index(reactions.stream().map(GooReactionFlows.Flow::of).toList());
        SortedSet<String> fluids = new TreeSet<>();
        GooTypes.BUNDLED.forEach(type -> fluids.add(GooTypes.id(type)));
        fluids.addAll(flows.yieldedBy().keySet());
        fluids.addAll(flows.spentBy().keySet());
        lines.add(GooAuditReport.EMPTY_LINE);
        lines.add(BY_FLUID);
        for (String fluid : fluids) {
            lines.add(FLUID_INDENT + fluid);
            lines.add(YIELDED_BY + joined(flows.yieldedBy().get(fluid)));
            lines.add(SPENT_BY + joined(flows.spentBy().get(fluid)));
        }
    }

    private static String joined(SortedSet<Identifier> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return NONE;
        }
        return reactions.stream().map(Identifier::toString).collect(Collectors.joining(LIST_JOIN));
    }
}

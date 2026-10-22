package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Value comparison audit sections: conflicts and the all-values listing. Called by {@link GooAuditReport} during the
 * audit report build.
 */
final class GooAuditValues {

    // --- Conflict constants ---

    /**
     * Conflicts section header prefix.
     */
    private static final String HDR_CONFLICTS_PREFIX = "CONFLICTS (";
    /**
     * Conflicts section header middle.
     */
    private static final String HDR_CONFLICTS_MID = " total, ";
    /**
     * Conflicts section header suffix.
     */
    private static final String HDR_CONFLICTS_SUFFIX = " recipe-cheaper)";
    /**
     * No conflicts message.
     */
    private static final String MSG_NO_CONFLICTS = "  No base/recipe value conflicts.";
    /**
     * Conflicts chat prefix.
     */
    private static final String MSG_CONFLICTS_CHAT_PREFIX = "  Conflicts: ";
    /**
     * Conflicts chat open paren.
     */
    private static final String MSG_CONFLICTS_CHAT_MID = " (";
    /**
     * Conflicts chat suffix.
     */
    private static final String MSG_CONFLICTS_CHAT_SUFFIX = " recipe-cheaper)";
    /**
     * Goo label in conflict lines.
     */
    private static final String LABEL_GOO_OPEN = " goo {";
    /**
     * Close brace in conflict lines.
     */
    private static final String LABEL_GOO_CLOSE = "}";
    /**
     * Recipe cheaper direction label.
     */
    private static final String DIR_RECIPE_CHEAPER = "<- RECIPE CHEAPER";
    /**
     * Base cheaper direction label.
     */
    private static final String DIR_BASE_CHEAPER = "<- BASE CHEAPER";
    /**
     * Conflict base prefix.
     */
    private static final String CONFLICT_BASE = " - base: ";
    /**
     * Conflict vs separator.
     */
    private static final String CONFLICT_VS = " vs recipe: ";

    /**
     * Equals-space-value format prefix.
     */
    private static final String LABEL_EQ_VALUE = " = ";
    /**
     * Open brace for value display.
     */
    private static final String LABEL_OPEN_BRACE = " {";
    /**
     * Base provenance tag.
     */
    private static final String LABEL_BASE = " (base)";

    // --- All Values constants ---

    /**
     * All values section header prefix.
     */
    private static final String HDR_ALL_PREFIX = "ALL VALUES (";
    /**
     * All values section header suffix.
     */
    private static final String HDR_ALL_SUFFIX = " items)";
    /**
     * Provenance: base+derived prefix.
     */
    private static final String PROV_BASE_DERIVED = " (base+derived from: ";
    /**
     * Provenance: derived prefix.
     */
    private static final String PROV_DERIVED = " (derived from: ";
    /**
     * Plus separator in recipe format.
     */
    private static final String RECIPE_PLUS = " + ";
    /**
     * Multiplier prefix in recipe format.
     */
    private static final String RECIPE_TIMES_PREFIX = "x ";
    /**
     * Arrow to result count in recipe format.
     */
    private static final String RECIPE_ARROW = " -> ";

    private GooAuditValues() {
    }

    // --- Conflicts ---

    /**
     * Appends the conflicts section to the report, returns count of recipe-cheaper conflicts.
     *
     * @param values the server's goo value registry
     * @param report the report lines list to append to
     * @return the number of recipe-cheaper conflicts
     */
    static int appendConflictsSection(GooValueRegistry values, List<String> report) {
        List<GooValueRegistry.ValueConflict> conflicts = values.diagnostics().conflicts();
        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        GooAuditReport.appendSectionBody(report,
                HDR_CONFLICTS_PREFIX + conflicts.size() + HDR_CONFLICTS_MID + exploitCount + HDR_CONFLICTS_SUFFIX,
                MSG_NO_CONFLICTS, conflicts, GooAuditValues::formatConflictFileLine);
        return (int) exploitCount;
    }

    /**
     * Sends a conflict count summary to chat.
     *
     * @param values the server's goo value registry
     * @param ctx the command context
     */
    static void sendConflictsSummary(GooValueRegistry values, CommandContext<CommandSourceStack> ctx) {
        List<GooValueRegistry.ValueConflict> conflicts = values.diagnostics().conflicts();
        if (conflicts.isEmpty()) {
            return;
        }

        long exploitCount = conflicts.stream().filter(GooValueRegistry.ValueConflict::isRecipeCheaper).count();
        ctx.getSource().sendSuccess(() ->
                Component.literal(MSG_CONFLICTS_CHAT_PREFIX + conflicts.size() + MSG_CONFLICTS_CHAT_MID + exploitCount + MSG_CONFLICTS_CHAT_SUFFIX)
                        .withStyle(exploitCount > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);
    }

    /**
     * Formats a single conflict as a plain-text line.
     *
     * @param conflict the value conflict to format
     * @return the formatted line
     */
    private static String formatConflictFileLine(GooValueRegistry.ValueConflict conflict) {
        String baseStr = conflict.baseValue().totalExact() + LABEL_GOO_OPEN + conflict.baseValue() + LABEL_GOO_CLOSE;
        String recipeStr = conflict.recipeValue().totalExact() + LABEL_GOO_OPEN + conflict.recipeValue() + LABEL_GOO_CLOSE;
        String direction = conflict.isRecipeCheaper() ? DIR_RECIPE_CHEAPER : DIR_BASE_CHEAPER;
        return GooAuditReport.INDENT + conflict.item() + CONFLICT_BASE + baseStr + CONFLICT_VS + recipeStr + GooAuditReport.SEP_SPACE + direction;
    }

    // --- All Values ---

    /**
     * Appends a section listing every item's effective value and its source.
     *
     * @param values the server's goo value registry
     * @param report the report lines list to append to
     */
    static void appendAllValuesSection(GooValueRegistry values, List<String> report) {
        Map<Identifier, GooValue> effective = values.table().getEffectiveValues();
        List<Identifier> sorted = new ArrayList<>(effective.keySet());
        Collections.sort(sorted);
        GooAuditReport.appendSectionBody(report, HDR_ALL_PREFIX + sorted.size() + HDR_ALL_SUFFIX,
                GooAuditReport.EMPTY_LINE, sorted, id -> formatValueLine(values, id, effective.get(id)));
    }

    /**
     * Formats a single item's value with its provenance (base/derived + source recipe).
     *
     * @param values the server's goo value registry
     * @param itemId the item identifier
     * @param value  the item's goo value
     * @return the formatted line
     */
    private static String formatValueLine(GooValueRegistry values, Identifier itemId, GooValue value) {
        StringBuilder sb = new StringBuilder();
        sb.append(GooAuditReport.INDENT).append(itemId).append(LABEL_EQ_VALUE).append(describeValue(value));
        appendProvenance(values, sb, itemId);
        return sb.toString();
    }

    /**
     * The value's exact total and each type's exact amount, like "64/3 {leaf: 64/3}".
     * Decision values-are-fractions-melt-floors-fabricate-ceils: the audit reads the exact
     * fraction, so a base value is set from what derivation actually holds.
     *
     * @param value the goo value
     * @return the exact description
     */
    static String describeValue(GooValue value) {
        return value.totalExact() + LABEL_OPEN_BRACE + value + LABEL_GOO_CLOSE;
    }

    /**
     * Appends the provenance tag: (base), (derived), or (base+derived).
     *
     * @param values the server's goo value registry
     * @param sb     the string builder to append to
     * @param itemId the item identifier
     */
    private static void appendProvenance(GooValueRegistry values, StringBuilder sb, Identifier itemId) {
        boolean hasBase = values.table().hasBaseValue(itemId);
        RecipeInput source = values.diagnostics().derivationSources().get(itemId);
        if (hasBase && source != null) {
            sb.append(PROV_BASE_DERIVED).append(formatSourceRecipe(values, source)).append(GooAuditReport.MSG_CLOSE_PAREN);
        } else if (hasBase) {
            sb.append(LABEL_BASE);
        } else if (source != null) {
            sb.append(PROV_DERIVED).append(formatSourceRecipe(values, source)).append(GooAuditReport.MSG_CLOSE_PAREN);
        }
    }

    /**
     * Formats a source recipe as a compact ingredient list.
     *
     * @param values the server's goo value registry
     * @param recipe the source recipe input
     * @return the formatted recipe string
     */
    private static String formatSourceRecipe(GooValueRegistry values, RecipeInput recipe) {
        Map<Identifier, Integer> counts = countIngredients(values, recipe);
        String ingredientStr = counts.entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getValue() + RECIPE_TIMES_PREFIX + e.getKey() : e.getKey().toString())
                .collect(Collectors.joining(RECIPE_PLUS));
        return recipe.resultCount() > 1
                ? ingredientStr + RECIPE_ARROW + recipe.resultCount() : ingredientStr;
    }

    /**
     * Finds the cheapest valued item ID among alternatives.
     *
     * @param values       the server's goo value registry
     * @param alternatives the set of alternative item IDs
     * @return the cheapest item ID, or null if none have values
     */
    private static Identifier findCheapestIngredientId(GooValueRegistry values, Set<Identifier> alternatives) {
        return IGooValueLookup.findCheapestAmong(alternatives, values.table()::lookup);
    }

    /**
     * Counts how many times each cheapest ingredient appears in a recipe.
     *
     * @param values the server's goo value registry
     * @param recipe the source recipe input
     * @return ingredient counts keyed by cheapest item ID
     */
    private static Map<Identifier, Integer> countIngredients(GooValueRegistry values, RecipeInput recipe) {
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        for (Set<Identifier> alts : recipe.ingredientAlternatives()) {
            Identifier cheapest = findCheapestIngredientId(values, alts);
            if (cheapest != null) {
                counts.merge(cheapest, 1, Integer::sum);
            }
        }
        return counts;
    }

}

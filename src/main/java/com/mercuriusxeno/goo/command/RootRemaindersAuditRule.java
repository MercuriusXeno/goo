package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooRootRemainders;
import com.mercuriusxeno.goo.data.RecipeInput;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * ROOT REMAINDERS: per root _roots names, the items it solves and their count, then every recipe among them
 * with its input total, output count, value per output and remainder at the root, the recipes whose output
 * count does not divide their input whole first (decision workbench-shows-each-roots-remainders-and-items-solved).
 */
@AuditRule(order = 120)
public final class RootRemaindersAuditRule implements GooAuditRule {
    /**
     * The section's name, which its header leads with.
     */
    public static final String SECTION = "ROOT REMAINDERS";
    private static final String HEADER_OPEN = " (";
    private static final String HEADER_SUFFIX = " roots)";
    private static final String INDENT = "  ";
    private static final String SPACE = " ";
    private static final String SOLVES = " solves ";
    private static final String ITEMS = " items: ";
    private static final String SMALLEST = "; smallest remainder ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String RECIPE_JOIN = " + ";
    private static final String RECIPE_ARROW = " -> ";
    private static final String INPUT = ": input ";
    private static final String EACH = ", each ";
    private static final String REMAINDER = ", remainder ";
    private static final String DEFAULT_NAMESPACE = "minecraft";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<GooRootRemainders.RootSolution> solutions = GooRootRemainders.solveEachRoot(inputs.values());
        List<String> lines = new ArrayList<>();
        for (GooRootRemainders.RootSolution solution : solutions) {
            StringJoiner solved = new StringJoiner(LIST_SEPARATOR);
            solution.solved().forEach(item -> solved.add(name(item)));
            lines.add(name(solution.root()) + SOLVES + solution.solved().size() + ITEMS + solved + SMALLEST
                    + solution.smallestRemainder());
            solution.rows().forEach(row -> lines.add(INDENT + recipeText(row.recipe()) + INPUT + row.inputTotal()
                    + EACH + row.perOutput() + REMAINDER + row.remainder()));
        }
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + solutions.size() + HEADER_SUFFIX,
                List.copyOf(lines), 0, List.of());
    }

    private static String recipeText(RecipeInput recipe) {
        Map<Identifier, Integer> counts = new TreeMap<>();
        for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
            slot.stream().sorted().findFirst().ifPresent(pick -> counts.merge(pick, 1, Integer::sum));
        }
        StringJoiner inputs = new StringJoiner(RECIPE_JOIN);
        counts.forEach((id, count) -> inputs.add(count + SPACE + name(id)));
        return inputs + RECIPE_ARROW + recipe.resultCount() + SPACE + name(recipe.output());
    }

    private static String name(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}

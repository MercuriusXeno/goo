package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooRootRemainders;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import static com.mercuriusxeno.goo.command.GooAuditRecipeText.itemName;
import static com.mercuriusxeno.goo.command.GooAuditRecipeText.recipeText;

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
    private static final String SOLVES = " solves ";
    private static final String ITEMS = " items: ";
    private static final String SMALLEST = "; smallest remainder ";
    private static final String LIST_SEPARATOR = ", ";
    private static final String INPUT = ": input ";
    private static final String EACH = ", each ";
    private static final String REMAINDER = ", remainder ";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<GooRootRemainders.RootSolution> solutions = GooRootRemainders.solveEachRoot(inputs.values());
        List<String> lines = new ArrayList<>();
        for (GooRootRemainders.RootSolution solution : solutions) {
            StringJoiner solved = new StringJoiner(LIST_SEPARATOR);
            solution.solved().forEach(item -> solved.add(itemName(item)));
            lines.add(itemName(solution.root()) + SOLVES + solution.solved().size() + ITEMS + solved + SMALLEST
                    + solution.smallestRemainder());
            solution.rows().forEach(row -> lines.add(INDENT + recipeText(row.recipe()) + INPUT + row.inputTotal()
                    + EACH + row.perOutput() + REMAINDER + row.remainder()));
        }
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + solutions.size() + HEADER_SUFFIX,
                List.copyOf(lines), 0, List.of());
    }
}

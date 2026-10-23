package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooUndercutChains;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import static com.mercuriusxeno.goo.command.GooAuditRecipeText.itemName;

/**
 * UNDERCUT MANUAL VALUES: each hand-keyed item the undercut litmus caught, its keyed and derived values, then each
 * chain of items the derived value came through from a hand-keyed entry, each backward solve marked. The litmus is
 * a heuristic, so each item is flagged for the operator to judge: tune or remove the value, or judge the recipe.
 * decision workbench-traces-each-undercut-manual-value-to-its-roots
 */
@AuditRule(order = 45)
public final class UndercutManualValuesAuditRule implements GooAuditRule {
    /**
     * The section's name, which its header leads with.
     */
    public static final String SECTION = "UNDERCUT MANUAL VALUES";
    private static final String HEADER_OPEN = " (";
    private static final String HEADER_SUFFIX = " items)";
    private static final String INDENT = "  ";
    private static final String CHAIN_INDENT = "    ";
    private static final String KEYED = ": keyed ";
    private static final String DERIVED = ", derived ";
    private static final String CHAIN_ARROW = " -> ";
    private static final String BACKWARD = " (solved backward)";
    private static final String NO_MARK = "";
    private static final String FLAG_UNDERCUT = " keyed value undercut by a cheaper derived value";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<GooUndercutChains.Undercut> undercuts = GooUndercutChains.traceEachUndercut(inputs.values());
        List<String> lines = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        for (GooUndercutChains.Undercut undercut : undercuts) {
            lines.add(INDENT + itemName(undercut.item()) + KEYED + GooAuditValues.describeValue(undercut.keyed())
                    + DERIVED + GooAuditValues.describeValue(undercut.derived()));
            undercut.chains().forEach(chain -> lines.add(CHAIN_INDENT + chainText(chain)));
            flags.add(undercut.item() + FLAG_UNDERCUT);
        }
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + undercuts.size() + HEADER_SUFFIX,
                List.copyOf(lines), undercuts.size(), List.copyOf(flags));
    }

    private static String chainText(List<GooUndercutChains.Step> chain) {
        StringJoiner text = new StringJoiner(CHAIN_ARROW);
        chain.forEach(step -> text.add(itemName(step.item()) + (step.solvedBackward() ? BACKWARD : NO_MARK)));
        return text.toString();
    }
}

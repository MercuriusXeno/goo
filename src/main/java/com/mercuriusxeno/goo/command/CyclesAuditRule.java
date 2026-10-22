package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the recipe cycles, each dead one flagged (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 30)
public final class CyclesAuditRule implements GooAuditRule {
    // A dead cycle is one no base value anchors.
    private static final String DEAD_MARK = "[DEAD]";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        int issues = GooAuditReport.appendCyclesSection(inputs.values(), appended);
        return GooAuditSection.withFlagsMarked(appended, issues, DEAD_MARK);
    }
}

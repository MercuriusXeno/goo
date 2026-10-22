package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing each goo type's least value, worked from the recipe graph (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 50)
public final class LeastValuePerTypeAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        GooAuditReport.appendLeastValueSection(inputs.values(), appended);
        return GooAuditSection.withIssueFlags(appended, 0);
    }
}

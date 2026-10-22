package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the registered items with no goo value (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 60)
public final class NoValueAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        GooAuditReport.appendNoValueSection(inputs.values(), inputs.registeredItems(), appended);
        return GooAuditSection.withIssueFlags(appended, 0);
    }
}

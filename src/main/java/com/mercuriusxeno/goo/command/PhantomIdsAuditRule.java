package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the ids base_values.json names that no registered item holds (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 20)
public final class PhantomIdsAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        int issues = GooAuditReport.appendPhantomIdsSection(inputs.values(), inputs.registeredItems(), appended);
        return GooAuditSection.withIssueFlags(appended, issues);
    }
}

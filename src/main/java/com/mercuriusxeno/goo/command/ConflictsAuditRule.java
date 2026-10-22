package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the items whose base value and recipe value disagree (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 40)
public final class ConflictsAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        int issues = GooAuditValues.appendConflictsSection(inputs.values(), appended);
        return GooAuditSection.withIssueFlags(appended, issues);
    }
}

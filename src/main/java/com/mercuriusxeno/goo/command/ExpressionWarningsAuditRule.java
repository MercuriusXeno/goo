package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the expressions base_values.json holds that the validator warns on (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 10)
public final class ExpressionWarningsAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        int issues = GooAuditReport.appendExpressionWarningsSection(inputs.values(), appended);
        return GooAuditSection.withIssueFlags(appended, issues);
    }
}

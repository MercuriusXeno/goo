package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing the reactions the server loaded (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 80)
public final class ReactionsAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        GooAuditFlows.appendReactionsSection(inputs.reactions(), appended);
        return GooAuditSection.withIssueFlags(appended, 0);
    }
}

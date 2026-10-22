package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing each constant and item with the entries consuming it (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 70)
public final class FormulasAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        GooAuditFlows.appendFormulasSection(inputs.values(), appended);
        return GooAuditSection.withIssueFlags(appended, 0);
    }
}

package com.mercuriusxeno.goo.command;

import java.util.ArrayList;
import java.util.List;

/**
 * The audit's section listing every item's value and where it came from (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 90)
public final class AllValuesAuditRule implements GooAuditRule {
    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> appended = new ArrayList<>();
        GooAuditValues.appendAllValuesSection(inputs.values(), appended);
        return GooAuditSection.withIssueFlags(appended, 0);
    }
}

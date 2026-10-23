package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.AnnotatedClasses;
import java.util.List;

/**
 * Finds every {@link AuditRule} class, so the audit walks whatever rules stand and a new concern is one class
 * (decision each-check-is-one-rule-added-alone).
 */
public final class GooAuditRules {
    private static final String RULE_PACKAGE = GooAuditRules.class.getPackageName();

    private GooAuditRules() {
    }

    /**
     * @return every discovered rule, in its declared order, then by class name
     */
    public static List<GooAuditRule> discovered() {
        return AnnotatedClasses.instances(AuditRule.class, GooAuditRule.class, RULE_PACKAGE,
                type -> type.getAnnotation(AuditRule.class).order());
    }
}

package com.mercuriusxeno.goo.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link GooAuditRule} the audit discovers and walks, so a new concern is one class and nothing else
 * changes (decision each-check-is-one-rule-added-alone). The class holds a public no-argument constructor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditRule {

    /**
     * @return where the rule's section stands in the report, lowest first
     */
    int order();
}

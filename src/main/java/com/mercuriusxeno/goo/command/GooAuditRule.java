package com.mercuriusxeno.goo.command;

/**
 * One audit check: it reads the values, recipes and reactions the registry hands the audit and answers its
 * section as data (decision each-check-is-one-rule-added-alone). Mark an implementation {@link AuditRule} for
 * the audit to discover it.
 */
@FunctionalInterface
public interface GooAuditRule {

    /**
     * @param inputs what the audit reads
     * @return the rule's section
     */
    GooAuditSection run(GooAuditInputs inputs);
}

package com.mercuriusxeno.goo.workbench.checks;

import java.util.List;

/**
 * One type-source check the workbench runs over the draft on every save: it reads the draft's values and
 * answers the items it wants, each with what it lacks (decision type-source-checks-are-rules-that-gate-promote).
 * Mark an implementation {@link ValuesCheckRule} for the workbench to discover it, so a new check is one class
 * (decision each-check-is-one-rule-added-alone).
 */
public interface ValuesCheck {

    /**
     * @return the content decision the check stands for, by key
     */
    String title();

    /**
     * @param values the draft's values
     * @return the items the check wants, empty where it passes
     */
    List<Want> wants(DraftValues values);
}

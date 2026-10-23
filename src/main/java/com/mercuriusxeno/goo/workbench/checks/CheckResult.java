package com.mercuriusxeno.goo.workbench.checks;

import java.util.List;

/**
 * One check's answer over the draft.
 *
 * @param title the check's content decision, by key
 * @param wants the items it wants, empty where it passes
 */
public record CheckResult(String title, List<Want> wants) {

    /**
     * @return whether the check wants nothing
     */
    public boolean passed() {
        return wants.isEmpty();
    }
}

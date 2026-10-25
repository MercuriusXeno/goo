package com.mercuriusxeno.goo.client.workbench;

/**
 * The values manager's tabs, in the page's order, labeled Items, Groups, Prefabs, Mutators, Audit, Checks: the page's
 * Values, Variables and Processes renamed to plain words, the constants keeping their names
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public enum ManagerTab {
    /** Each item and tag's entry and its resolved values. */
    VALUES("Items"),
    /** The groups and their members. */
    GROUPS("Groups"),
    /** The _constants section. */
    VARIABLES("Prefabs"),
    /** The conversions, post-conversions, additives and parallel copies. */
    PROCESSES("Mutators"),
    /** The audit's sections. */
    AUDIT("Audit"),
    /** The type-source checks. */
    CHECKS("Checks");

    private final String label;

    ManagerTab(String label) {
        this.label = label;
    }

    /**
     * @return the tab's label on the tab bar
     */
    public String label() {
        return label;
    }
}

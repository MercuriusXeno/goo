package com.mercuriusxeno.goo.client.workbench;

/**
 * The values manager's tabs, in the page's order (decision goo-workbench-opens-the-in-game-manager-screen).
 */
public enum ManagerTab {
    /** Each item and tag's entry and its resolved values. */
    VALUES("Values"),
    /** The groups and their members. */
    GROUPS("Groups"),
    /** The _constants section. */
    VARIABLES("Variables"),
    /** The conversions, post-conversions, additives and parallel copies. */
    PROCESSES("Processes"),
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

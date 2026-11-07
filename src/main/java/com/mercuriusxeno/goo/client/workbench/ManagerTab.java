package com.mercuriusxeno.goo.client.workbench;

/**
 * The values manager's tabs, in the page's order, labeled Items, #Groups, $Prefabs, Conversions, Audit, Abilities: the page's
 * Values, Variables and Processes renamed, the constants keeping their names, groups marked # and prefabs $ as
 * every name of theirs is (decisions goo-workbench-opens-the-in-game-manager-screen,
 * values-checks-go-with-the-checks-tab, conversions-is-the-one-section-and-the-pane-name,
 * hash-marks-groups-and-dollar-marks-prefabs-everywhere).
 */
public enum ManagerTab {
    /** Each item and tag's entry and its resolved values. */
    VALUES("Items"),
    /** The groups and their members. */
    GROUPS("#Groups"),
    /** The _constants section. */
    VARIABLES("$Prefabs"),
    /** The _conversions section: its formulas, additives, stacks and parallel copies. */
    PROCESSES("Conversions"),
    /** The audit's sections. */
    AUDIT("Audit"),
    /** Every ability's numeric params (decision abilities-tab-lists-every-numeric-param-as-expression-boxes). */
    ABILITIES("Abilities");

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

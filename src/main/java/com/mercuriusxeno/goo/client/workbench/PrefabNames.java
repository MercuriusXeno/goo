package com.mercuriusxeno.goo.client.workbench;

/**
 * The one spelling of the mark every prefab name draws after, wherever the manager draws one (decisions
 * hash-marks-groups-and-dollar-marks-prefabs-everywhere, diagnose-and-correct-prefabs-drawn-without-their-dollar).
 */
final class PrefabNames {
    /** The mark a prefab's name draws after. */
    static final String PREFAB_MARK = "$";

    private PrefabNames() {
    }

    /**
     * @param name a prefab's name
     * @return the name as the manager draws it, after its mark
     */
    static String marked(String name) {
        return PREFAB_MARK + name;
    }
}

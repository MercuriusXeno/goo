package com.mercuriusxeno.goo.client.workbench;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The entries each tab marked for removal, held beside the draft until a save removes them or a restore or a Revert
 * clears them, so a delete stays undoable until the draft commits.
 * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
 */
final class RemovalMarks {
    /** The tabs whose rows a ✗ marks. */
    enum Tab {
        PREFABS,
        GROUPS,
        MUTATORS
    }

    private final Map<Tab, Set<String>> marks = new EnumMap<>(Tab.class);

    /**
     * @param tab the tab the entry lists on
     * @param key the entry's key
     */
    void mark(Tab tab, String key) {
        marks.computeIfAbsent(tab, each -> new LinkedHashSet<>()).add(key);
    }

    /**
     * @param tab the tab the entry lists on
     * @param key the entry's key
     */
    void restore(Tab tab, String key) {
        Set<String> marked = marks.get(tab);
        if (marked != null) {
            marked.remove(key);
        }
    }

    /**
     * @param tab the tab the entry lists on
     * @param key the entry's key
     * @return whether the save removes it
     */
    boolean isMarked(Tab tab, String key) {
        Set<String> marked = marks.get(tab);
        return marked != null && marked.contains(key);
    }

    /**
     * @param tab a tab
     * @return its marked keys in the order they were marked
     */
    List<String> marked(Tab tab) {
        Set<String> marked = marks.get(tab);
        return marked == null ? List.of() : List.copyOf(marked);
    }

    /**
     * Clears every tab's marks, as a Revert does.
     */
    void clear() {
        marks.clear();
    }
}

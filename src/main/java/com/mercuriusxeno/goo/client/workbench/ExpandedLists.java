package com.mercuriusxeno.goo.client.workbench;

import java.util.HashSet;
import java.util.Set;

/**
 * Which used-by lists and group rows stand expanded: their ..+n button adds a list's key and its Hide
 * button removes it, collapsing the list to its first row (decision expanded-used-by-lists-take-a-hide-button).
 */
final class ExpandedLists {
    private final Set<String> keys = new HashSet<>();

    /**
     * @param key the list's key
     * @return whether the list stands expanded
     */
    boolean expanded(String key) {
        return keys.contains(key);
    }

    /**
     * @param key the list whose more button was pressed
     */
    void expand(String key) {
        keys.add(key);
    }

    /**
     * @param key the list whose Hide button was pressed
     */
    void hide(String key) {
        keys.remove(key);
    }
}

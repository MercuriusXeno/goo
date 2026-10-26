package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that more expands a list and Hide collapses it again, for the keys Groups and Prefabs keep
 * (decision expanded-used-by-lists-take-a-hide-button).
 */
class ExpandedListsTest {
    /**
     * Expand then hide answers collapsed; hide then expand answers expanded.
     *
     * @param key a Groups row key, or a Prefabs direct or indirect key
     */
    @ParameterizedTest
    @ValueSource(strings = {"ores", "waxed:direct", "waxed:indirect"})
    void hideCollapsesAndMoreExpandsAgain(String key) {
        ExpandedLists lists = new ExpandedLists();
        lists.expand(key);
        assertTrue(lists.expanded(key));
        lists.hide(key);
        assertFalse(lists.expanded(key));
        lists.expand(key);
        assertTrue(lists.expanded(key));
    }
}

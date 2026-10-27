package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the searchable dropdown's filtering, its icons and its pick, with no Screen
 * (decision expression-builder-composes-every-form-without-typing).
 */
class SearchDropdownTest {
    private static final List<ValuesTab.ManagerEntry> INVENTORY = List.of(
            new ValuesTab.ManagerEntry("minecraft:copper_ingot", "Copper Ingot", false),
            new ValuesTab.ManagerEntry("minecraft:copper_block", "Block of Copper", false),
            new ValuesTab.ManagerEntry("minecraft:stick", "Stick", false),
            new ValuesTab.ManagerEntry("#minecraft:logs", "2 members", true));

    /**
     * Typed text keeps the items and prefabs whose name or id holds it, each item carrying its id for its icon and
     * each prefab none, and a tag never lists.
     */
    @Test
    void typedTextFiltersItemsWithIconsAndPrefabs() {
        SearchDropdown dropdown = SearchDropdown.over(INVENTORY, List.of("copper_nugget", "base"), true);
        dropdown.search("cop");
        List<SearchDropdown.Choice> choices = dropdown.choices();
        assertEquals(List.of("minecraft:copper_ingot", "minecraft:copper_block", "copper_nugget"),
                choices.stream().map(SearchDropdown.Choice::id).toList());
        assertEquals("minecraft:copper_ingot", choices.getFirst().iconId());
        assertNull(choices.get(2).iconId());
        assertEquals(SearchDropdown.Kind.PREFAB, choices.get(2).kind());
        dropdown.search("");
        assertTrue(dropdown.choices().stream().noneMatch(choice -> choice.id().startsWith("#")));
    }

    /**
     * A dropdown over prefabs alone lists no item.
     */
    @Test
    void prefabsAloneListNoItem() {
        SearchDropdown dropdown = SearchDropdown.over(INVENTORY, List.of("copper_nugget", "base"), false);
        dropdown.search("");
        assertEquals(List.of("copper_nugget", "base"), dropdown.choices().stream().map(SearchDropdown.Choice::id)
                .toList());
    }

    /**
     * A list longer than the rows it shows scrolls under the wheel, so the last prefab is reachable and a click on
     * the bottom row picks it; the wheel stops at the list's end.
     */
    @Test
    void wheelScrollsALongListSoTheLastPrefabIsPickable() {
        SearchDropdown dropdown = SearchDropdown.over(INVENTORY, twelvePrefabs(), false);
        dropdown.open();
        for (int notch = 0; notch < 10; notch++) {
            dropdown.scroll(-1);
        }
        assertEquals("prefab11", pickAt(dropdown, SearchDropdown.SHOWN - 1).id());
    }

    /**
     * A new search starts the list from its first match again.
     */
    @Test
    void searchReturnsTheListToItsStart() {
        SearchDropdown dropdown = SearchDropdown.over(INVENTORY, twelvePrefabs(), false);
        dropdown.open();
        dropdown.scroll(-1);
        dropdown.search("prefab");
        assertEquals("prefab0", pickAt(dropdown, 0).id());
    }

    private static List<String> twelvePrefabs() {
        return IntStream.range(0, 12).mapToObj(index -> "prefab" + index).toList();
    }

    private static SearchDropdown.Choice pickAt(SearchDropdown dropdown, int row) {
        ClickTargets targets = new ClickTargets();
        AtomicReference<SearchDropdown.Choice> picked = new AtomicReference<>();
        dropdown.layTargets(targets, 0, 0, 120, picked::set);
        assertTrue(targets.click(5, row * SearchDropdown.ROW_HEIGHT + 1));
        return picked.get();
    }

    /**
     * One click opens the list and a click on a choice answers its namespaced id and closes it.
     */
    @Test
    void openClickThenChoiceClickAnswersTheNamespacedId() {
        SearchDropdown dropdown = SearchDropdown.over(INVENTORY, List.of(), true);
        ClickTargets targets = new ClickTargets();
        dropdown.addOpener(targets, 0, 0, 40, 10);
        assertTrue(targets.click(3, 3));
        assertTrue(dropdown.isOpen());
        dropdown.search("block");
        targets.clear();
        AtomicReference<SearchDropdown.Choice> picked = new AtomicReference<>();
        dropdown.layTargets(targets, 10, 20, 120, picked::set);
        assertTrue(targets.click(15, 20 + SearchDropdown.ROW_HEIGHT / 2));
        assertEquals("minecraft:copper_block", picked.get().id());
        assertFalse(dropdown.isOpen());
    }
}

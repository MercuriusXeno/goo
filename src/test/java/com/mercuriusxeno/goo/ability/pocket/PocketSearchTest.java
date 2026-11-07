package com.mercuriusxeno.goo.ability.pocket;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers which of Pocket's entries its search bar leaves standing. */
class PocketSearchTest {

    private static final List<String> NAMES = List.of("Cobblestone", "Oak Log", "Stone Bricks", "Diamond");

    @Test
    void blankSearchShowsEveryEntry() {
        assertEquals(List.of(0, 1, 2, 3), PocketSearch.visible(NAMES, "  "));
    }

    @Test
    void searchKeepsTheEntriesWhoseNameHoldsItIgnoringCase() {
        assertEquals(List.of(0, 2), PocketSearch.visible(NAMES, "STONE"));
    }
}

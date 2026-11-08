package com.mercuriusxeno.goo.client.knownitems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The press of G that opens the overlay, and its repeats, type nothing; once G is released typing
 * fills the bar and backspace erases it (decision menu-lists-known-items-alphabetically-with-search-and-scroll).
 */
class SearchInputTest {

    @Test
    void openingKeyAndItsRepeatsTypeNothing() {
        SearchInput input = new SearchInput(true);
        assertFalse(input.type("g"));
        assertFalse(input.type("g"));
        assertEquals("", input.text());
    }

    @Test
    void typingFillsTheBarOnceTheOpeningKeyIsReleased() {
        SearchInput input = new SearchInput(true);
        input.type("g");
        input.releaseOpeningKey();
        assertTrue(input.type("s"));
        input.type("t");
        assertEquals("st", input.text());
    }

    @Test
    void openingKeyReleasedBeforeTheOverlayOpensTypesTheFirstCharacterAtOnce() {
        SearchInput input = new SearchInput(false);
        assertTrue(input.type("s"));
        assertEquals("s", input.text());
    }

    @Test
    void backspaceErasesTheLastCharacter() {
        SearchInput input = new SearchInput(true);
        input.releaseOpeningKey();
        input.type("ö");
        input.type("l");
        assertTrue(input.eraseLast());
        assertEquals("ö", input.text());
        input.eraseLast();
        assertFalse(input.eraseLast());
    }
}

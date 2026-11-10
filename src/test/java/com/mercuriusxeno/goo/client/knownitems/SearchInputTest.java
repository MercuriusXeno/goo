package com.mercuriusxeno.goo.client.knownitems;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typing fills the search bar and backspace erases its last character, a whole code point
 * (decision menu-lists-known-items-alphabetically-with-search-and-scroll).
 */
class SearchInputTest {

    @Test
    void typingFillsTheBar() {
        SearchInput input = new SearchInput();
        input.type("s");
        input.type("t");
        assertEquals("st", input.text());
    }

    @Test
    void backspaceErasesTheLastCharacter() {
        SearchInput input = new SearchInput();
        input.type("ö");
        input.type("l");
        assertTrue(input.eraseLast());
        assertEquals("ö", input.text());
        input.eraseLast();
        assertFalse(input.eraseLast());
    }
}

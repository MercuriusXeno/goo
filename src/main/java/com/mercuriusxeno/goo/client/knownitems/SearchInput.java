package com.mercuriusxeno.goo.client.knownitems;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The known items search bar's text, held one code point to an entry, so backspace erases a whole
 * character.
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 */
public final class SearchInput {

    /** The characters join with nothing between them. */
    private static final String NO_SEPARATOR = "";

    private final Deque<String> typed = new ArrayDeque<>();

    /**
     * Types characters into the bar.
     *
     * @param chars the characters typed
     */
    public void type(String chars) {
        chars.codePoints().mapToObj(Character::toString).forEach(typed::addLast);
    }

    /**
     * Erases the last character typed.
     *
     * @return true when a character was erased
     */
    public boolean eraseLast() {
        return typed.pollLast() != null;
    }

    /**
     * The text typed so far.
     *
     * @return the bar's text
     */
    public String text() {
        return String.join(NO_SEPARATOR, typed);
    }
}

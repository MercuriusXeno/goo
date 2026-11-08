package com.mercuriusxeno.goo.client.knownitems;

/**
 * The known items search bar's text.
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 */
public final class SearchInput {

    private static final int ONE_CODE_POINT_BACK = -1;

    private final StringBuilder typed = new StringBuilder();

    /**
     * Types characters into the bar.
     *
     * @param chars the characters typed
     */
    public void type(String chars) {
        typed.append(chars);
    }

    /**
     * Erases the last character typed.
     *
     * @return true when a character was erased
     */
    public boolean eraseLast() {
        if (typed.isEmpty()) {
            return false;
        }
        typed.setLength(typed.offsetByCodePoints(typed.length(), ONE_CODE_POINT_BACK));
        return true;
    }

    /**
     * The text typed so far.
     *
     * @return the bar's text
     */
    public String text() {
        return typed.toString();
    }
}

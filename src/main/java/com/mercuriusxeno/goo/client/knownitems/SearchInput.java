package com.mercuriusxeno.goo.client.knownitems;

/**
 * The known items search bar's text. The press of G that opened the overlay is still down as it
 * opens, and its key repeats would type into the bar, so typing waits until that key is released.
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 * decision g-opens-the-known-items-menu-empty-handed
 */
public final class SearchInput {

    private static final int ONE_CODE_POINT_BACK = -1;

    private final StringBuilder typed = new StringBuilder();
    private boolean openingKeyHeld = true;

    /**
     * Types characters into the bar once the opening key is up.
     *
     * @param chars the characters typed
     * @return true when the characters went into the bar
     */
    public boolean type(String chars) {
        if (openingKeyHeld) {
            return false;
        }
        typed.append(chars);
        return true;
    }

    /** Marks the key that opened the overlay as released, so typing reaches the bar. */
    public void releaseOpeningKey() {
        openingKeyHeld = false;
    }

    /**
     * Whether the key that opened the overlay is still down.
     *
     * @return true until its release
     */
    public boolean openingKeyHeld() {
        return openingKeyHeld;
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

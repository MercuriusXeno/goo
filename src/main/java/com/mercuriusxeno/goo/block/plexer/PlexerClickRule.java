package com.mercuriusxeno.goo.block.plexer;

/**
 * What a right-click on the plexer does, on any face and with either hand: it opens the known items
 * menu, unless a canister in hand takes the click to place itself.
 * decision right-click-on-the-plexer-opens-the-known-items-menu
 */
public final class PlexerClickRule {

    /** What the held hand holds, as the click rule reads it. */
    public enum Held {
        /** Nothing. */
        EMPTY,
        /** A canister, which may place itself. */
        CANISTER,
        /** Any other item. */
        ITEM
    }

    /** What the click does. */
    public enum Click {
        /** Opens the known items menu for this plexer. */
        OPEN_MENU,
        /** Passes the click on to the held item's own use. */
        PASS
    }

    private PlexerClickRule() {}

    /**
     * Resolves a right-click on the plexer.
     *
     * @param held                what the clicking hand holds
     * @param canisterTakesClick  whether a held canister's own use takes this click
     * @return the click's outcome
     */
    public static Click resolve(Held held, boolean canisterTakesClick) {
        return held == Held.CANISTER && canisterTakesClick ? Click.PASS : Click.OPEN_MENU;
    }
}

package com.mercuriusxeno.goo.client.knownitems;

import org.lwjgl.glfw.GLFW;

/**
 * The known items key as a pure decision: G with an empty main hand, no glove held, no screen
 * open and at least one item known opens the known items overlay, and a held glove leaves the press
 * to the glove's radial.
 * decision g-opens-the-known-items-menu-empty-handed
 * decision menu-places-a-ghost-item-in-the-empty-hand
 */
public final class KnownItemsKeyGate {

    /** The known items mapping's translation key. */
    public static final String NAME_KEY = "key.goo.known_items";
    /** The known items mapping's default key. */
    public static final int DEFAULT_KEY = GLFW.GLFW_KEY_G;

    private KnownItemsKeyGate() {}

    /**
     * Whether a press of the known items key opens the overlay.
     *
     * @param mainHandEmpty whether the main hand holds nothing
     * @param gloveHeld     whether either hand holds a glove
     * @param screenOpen    whether a screen is open
     * @param knowsAnyItem  whether the player knows at least one item
     * @return true when the press opens the overlay
     */
    public static boolean pressOpensOverlay(boolean mainHandEmpty, boolean gloveHeld, boolean screenOpen,
                                            boolean knowsAnyItem) {
        return mainHandEmpty && !gloveHeld && !screenOpen && knowsAnyItem;
    }
}

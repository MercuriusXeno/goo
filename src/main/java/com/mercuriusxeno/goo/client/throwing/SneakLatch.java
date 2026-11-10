package com.mercuriusxeno.goo.client.throwing;

/**
 * Whether the player's sneak counts for working on portals: once a link
 * lands, the sneak still held stops counting until it is let go and pressed
 * again, so the portal just linked never lights up as aimed at and no second
 * link starts on the same press.
 * portals-link-by-looking-through-portal-vision
 */
public final class SneakLatch {

    private boolean latched;

    /**
     * Notes that a link landed, so the sneak held now stops counting.
     */
    public void linked() {
        latched = true;
    }

    /**
     * Notes whether the player sneaks this tick; letting go frees the sneak.
     *
     * @param sneaking whether it sneaks
     */
    public void update(boolean sneaking) {
        if (!sneaking) {
            latched = false;
        }
    }

    /**
     * @param sneaking whether the player sneaks
     * @return whether the sneak counts for working on portals
     */
    public boolean sneaking(boolean sneaking) {
        return sneaking && !latched;
    }
}

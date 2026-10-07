package com.mercuriusxeno.goo.ability.hearts;

/**
 * One held tick of Vital Reserve: health leaves the bar and comes back as
 * reserve hearts behind it at a lossy ratio. The drain stops at the floor,
 * so it takes the player's life but cannot kill them, and stops at the cap,
 * so draining, regenerating and draining again cannot grow the reserve past it.
 * reserve-hearts-sit-behind-the-bar
 *
 * @param drainHearts the hearts one held tick drains
 * @param ratio       the reserve hearts one drained heart banks
 * @param capHearts   the most reserve hearts that may stand
 * @param floorHearts the health the drain never takes the player under, in hearts
 */
public record ReserveDrain(float drainHearts, float ratio, float capHearts, float floorHearts) {

    /**
     * What a held tick leaves: the player's health and the overlay after it.
     *
     * @param health  the health to set, silently, with no damage event
     * @param overlay the overlay after the tick
     */
    public record Drawn(float health, HeartOverlay overlay) {
    }

    /**
     * Runs one held tick against the player's health and overlay. A tick
     * that can take nothing, at the floor or at the cap, leaves both alone.
     *
     * @param standing the overlay standing
     * @param health   the player's real health
     * @return the health and overlay after the tick
     */
    public Drawn draw(HeartOverlay standing, float health) {
        int capHalves = Math.round(capHearts * HeartOverlay.FULL_SHIELD);
        float taken = Math.min(drainHearts * HeartOverlay.HEART_POINTS, health - floorHearts * HeartOverlay.HEART_POINTS);
        boolean capped = standing.reserves() && standing.shieldHalves() >= capHalves;
        if (taken <= 0f || capped) {
            return new Drawn(health, standing);
        }
        return new Drawn(health - taken, standing.bank(taken, ratio, capHalves));
    }
}

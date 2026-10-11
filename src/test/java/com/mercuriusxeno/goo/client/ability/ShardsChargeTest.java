package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The fan of knives gathering at the shoulder while Shards charges: one
 * more knife as the charge grows, capped at what the fan shows, fanned
 * evenly about upright (decision shards-sling-then-morph-to-flechettes).
 */
class ShardsChargeTest {

    private static final Charge SHARDS = new Charge(30, 24, 50, 8);
    private static final float TOLERANCE = 1e-5f;

    @Test
    void theFanGainsKnivesAsTheChargeGrowsUpToWhatItShows() {
        assertEquals(1, ShardsCharge.shownAt(SHARDS, 0f));
        assertEquals(5, ShardsCharge.shownAt(SHARDS, 0.2f));
        assertEquals(ShardsCharge.MOST_SHOWN, ShardsCharge.shownAt(SHARDS, 1f));
    }

    @Test
    void theFanOpensEvenlyAboutUpright() {
        assertEquals(0f, ShardsCharge.fanAngle(0, 1), TOLERANCE);
        assertEquals(ShardsCharge.FAN_DEGREES / 2, ShardsCharge.fanAngle(0, 5), TOLERANCE);
        assertEquals(0f, ShardsCharge.fanAngle(2, 5), TOLERANCE);
        assertEquals(-ShardsCharge.FAN_DEGREES / 2, ShardsCharge.fanAngle(4, 5), TOLERANCE);
    }
}

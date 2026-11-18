package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The buffer's rules in half percents: a knife's share 9% and a javelin's
 * 30%; a weapon forms only on a share above the 10% floor; and the refill
 * climbs a tick's speed at a time to 100% less the shares held, and stops there.
 * decision palm-blob-is-a-buffer-that-right-click-turns-into-weapons
 * decision held-weapons-reserve-their-share-of-the-buffer
 */
class PalmBufferTest {

    private static final int KNIFE_SHARE = 18;
    private static final int JAVELIN_SHARE = 60;
    private static final int FLOOR = 20;
    private static final int SEVENTY = 140;
    private static final int TICKS = 100;

    @Test
    void aKnifeTakesNinePercentAndAJavelinThirty() {
        assertEquals(KNIFE_SHARE, PalmBuffer.shareOf(AbilityJson.decode("crystal_knives").delivery().charge()));
        assertEquals(JAVELIN_SHARE, PalmBuffer.shareOf(AbilityJson.decode("metal_dart").delivery().charge()));
    }

    @Test
    void aWeaponFormsOnlyOnAShareAboveTheFloor() {
        assertTrue(PalmBuffer.affords(FLOOR + JAVELIN_SHARE, JAVELIN_SHARE));
        assertFalse(PalmBuffer.affords(FLOOR + JAVELIN_SHARE - 1, JAVELIN_SHARE));
    }

    @Test
    void withOneJavelinHeldTheRefillClimbsToSeventyPercentAndStops() {
        int fill = FLOOR;
        for (int tick = 0; tick < TICKS; tick++) {
            fill = PalmBuffer.refilled(fill, JAVELIN_SHARE, PalmBuffer.REFILL_A_TICK);
        }
        assertEquals(SEVENTY, fill, "one javelin held should cap the refill at 70%");
    }

    // buffer-refills-at-a-constant-speed-set-by-the-glove
    @Test
    void eachCasterRefillsAtItsOwnSpeed() {
        assertEquals(1, PalmBuffer.refillSpeedOf(null), "bare handed, 0.5% a tick");
        assertEquals(2, PalmBuffer.refillSpeedOf(com.mercuriusxeno.goo.item.GooGloveItem.GloveTier.GLOVE),
                "the Goo Glove, 1% a tick");
        assertEquals(3, PalmBuffer.refillSpeedOf(com.mercuriusxeno.goo.item.GooGloveItem.GloveTier.GAUNTLET),
                "the Goo Gauntlet, 1.5% a tick");
        assertEquals(4, PalmBuffer.refillSpeedOf(com.mercuriusxeno.goo.item.GooGloveItem.GloveTier.EXO_GAUNTLET),
                "the Exo Gauntlet, 2% a tick");
    }}

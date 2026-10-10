package com.mercuriusxeno.goo.ability.bio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A second of Bio's toxin takes its share of max health times its amplitude.
 * bio-toxin-stacks-to-amplitude-two
 */
class ToxinDrainTest {

    private static final double SHARE = 0.06;
    private static final float ZOMBIE_HEALTH = 20f;
    private static final float TOLERANCE = 1e-4f;

    @Test
    void amplitudeTwoDrainsTwiceAmplitudeOne() {
        assertEquals(1.2f, ToxinDrain.perSecond(SHARE, ZOMBIE_HEALTH, 0), TOLERANCE);
        assertEquals(2.4f, ToxinDrain.perSecond(SHARE, ZOMBIE_HEALTH, 1), TOLERANCE);
    }
}

package com.mercuriusxeno.goo.ability.nether;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Invert's sky seam: it holds while a block stands between the player and the sky, and ends once the player is open
 * to it.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
class InvertSkyTest {

    /** A ceiling block's y, the lowest block over a room. */
    private static final int CEILING_Y = 64;

    @Test
    void holdsUnderARoof() {
        // a body resting against the ceiling's underside, the heightmap standing one over the ceiling block
        assertEquals(InvertSky.Verdict.HOLD, InvertSky.verdict(CEILING_Y, CEILING_Y + 1));
    }

    @Test
    void holdsWellUnderARoof() {
        assertEquals(InvertSky.Verdict.HOLD, InvertSky.verdict(CEILING_Y - 2.5, CEILING_Y + 1));
    }

    @Test
    void endsOpenToTheSky() {
        // a body standing on open ground, the heightmap one over the ground under its feet
        double feet = CEILING_Y;
        assertEquals(InvertSky.Verdict.END, InvertSky.verdict(feet + 1.8, CEILING_Y));
    }

    @Test
    void endsWithItsTopInTheHighestBlockSpace() {
        assertEquals(InvertSky.Verdict.END, InvertSky.verdict(CEILING_Y + 0.5, CEILING_Y));
    }
}

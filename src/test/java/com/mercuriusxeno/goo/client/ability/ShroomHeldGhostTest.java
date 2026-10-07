package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shroom's held ghost lays its spore motes on the shell, each a unit from the
 * center, spread from top to bottom (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class ShroomHeldGhostTest {

    private static final double EPSILON = 1e-9;

    @Test
    void everyMoteSitsOnTheUnitShell() {
        for (int i = 0; i < ShroomHeldGhost.MOTES; i++) {
            double[] mote = ShroomHeldGhost.moteOnUnitShell(i, 0.7);
            assertEquals(1, Math.sqrt(mote[0] * mote[0] + mote[1] * mote[1] + mote[2] * mote[2]), EPSILON);
        }
    }

    @Test
    void theMotesSpanTheShellFromTopToBottom() {
        assertTrue(ShroomHeldGhost.moteOnUnitShell(0, 0)[1] > 0.99);
        assertTrue(ShroomHeldGhost.moteOnUnitShell(ShroomHeldGhost.MOTES - 1, 0)[1] < -0.99);
    }
}

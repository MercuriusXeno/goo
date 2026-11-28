package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A link carrying a signal plainly outshines an idle one, so the beam shows whether a signal crosses it
 * (decision diagnose-and-correct-relay-ignoring-torch-and-metronome).
 */
class RelayLasersTest {

    /** How many times an idle link's glow a carrying link's must reach for the two to read apart at a glance. */
    private static final int GLOW_CONTRAST = 4;

    @Test
    void aCarryingLinkGlowsFourTimesAnIdleOne() {
        int carrying = glow(RelayLasers.laserColor(true));
        int idle = glow(RelayLasers.laserColor(false));
        assertTrue(carrying >= GLOW_CONTRAST * idle,
                "A carrying link should glow " + GLOW_CONTRAST + " times an idle one; carrying " + carrying
                        + ", idle " + idle);
    }

    /** The light an additive line adds: its alpha times the sum of its channels. */
    private static int glow(int argb) {
        return ARGB.alpha(argb) * (ARGB.red(argb) + ARGB.green(argb) + ARGB.blue(argb));
    }
}

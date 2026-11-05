package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the followed row's glow fade (decision followed-target-glows-briefly).
 */
class RevealGlowTest {

    /**
     * The glow starts at its full color, fades every frame, and is clear at its frame count and after.
     */
    @Test
    void glowFadesFromFullToNoneOverItsFrames() {
        assertEquals(RevealGlow.GLOW, RevealGlow.colorAt(0));
        for (int frame = 1; frame <= RevealGlow.FRAMES; frame++) {
            assertTrue(RevealGlow.alphaAt(frame) < RevealGlow.alphaAt(frame - 1), "frame " + frame);
        }
        assertEquals(0, RevealGlow.alphaAt(RevealGlow.FRAMES));
        assertEquals(0, RevealGlow.alphaAt(RevealGlow.FRAMES + 5));
    }
}

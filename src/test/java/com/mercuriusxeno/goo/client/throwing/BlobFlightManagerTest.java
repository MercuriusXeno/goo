package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.client.overlay.ArcRenderer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a flight's peak reads the distance from its start to its
 * endpoint at the throw, as the aim line does, so a throw at a mob arcs
 * (decision diagnose-then-fix-blob-off-the-line).
 */
class BlobFlightManagerTest {

    private static final Vec3 START = new Vec3(3, 64, -2);
    private static final Vec3 MOB_SIXTEEN_AWAY = START.add(0, 0, 16);
    private static final double PEAK_TOLERANCE = 1e-9;

    /**
     * A mob 16 blocks from the start gets the 1.6-block base peak the line drew.
     */
    @Test
    void mobSixteenBlocksAwayPeaksLikeTheLine() {
        assertEquals(ThrowArc.basePeak(16),
                BlobFlightManager.peakForFlight(START, MOB_SIXTEEN_AWAY, GooTypes.FROST, false), PEAK_TOLERANCE);
    }

    /**
     * A lob flight flies the peak the aim indicator draws for the same start
     * and top face, whether the hand sits below the face or above it
     * (decision lob-apex-at-top-face-height).
     */
    @Test
    void lobFlightFliesTheIndicatorPeak() {
        Vec3 faceAboveHand = START.add(0, 2, 8);
        Vec3 faceBelowHand = START.add(0, -2, 8);
        assertEquals(ArcRenderer.computeArcPeak(START, faceAboveHand, 1),
                BlobFlightManager.peakForFlight(START, faceAboveHand, GooTypes.FROST, true), PEAK_TOLERANCE);
        assertEquals(ArcRenderer.computeArcPeak(START, faceBelowHand, 1),
                BlobFlightManager.peakForFlight(START, faceBelowHand, GooTypes.FROST, true), PEAK_TOLERANCE);
    }

    /**
     * Glow flies straight at any distance.
     */
    @Test
    void glowFliesStraight() {
        assertEquals(0.0,
                BlobFlightManager.peakForFlight(START, MOB_SIXTEEN_AWAY, GooTypes.GLOW, false), PEAK_TOLERANCE);
    }
}

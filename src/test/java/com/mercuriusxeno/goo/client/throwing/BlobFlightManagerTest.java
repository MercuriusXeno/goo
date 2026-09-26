package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ThrowArc;
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
     * A granny arc at the same mob reads the boosted peak.
     */
    @Test
    void grannyArcReadsTheBoostedPeak() {
        assertEquals(ThrowArc.grannyPeak(16),
                BlobFlightManager.peakForFlight(START, MOB_SIXTEEN_AWAY, GooTypes.FROST, true), PEAK_TOLERANCE);
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

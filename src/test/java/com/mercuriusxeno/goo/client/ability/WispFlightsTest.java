package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispFlights: a wisp's flight leaves the glove, covers most of the way
 * early and settles into its cell; a wisp with no flight stands at its cell.
 * decision radiant-wisps-where-light-is-low
 */
class WispFlightsTest {

    private static final Vec3 GLOVE = new Vec3(0, 1.5, 0);
    private static final Vec3 HOME = new Vec3(20.5, 4.5, -8.5);
    private static final double TOLERANCE = 1e-6;

    @Test
    void theFlightLeavesTheGloveAndLandsInTheCell() {
        assertEquals(0, WispFlights.alongFlight(GLOVE, HOME, 0f).distanceTo(GLOVE), TOLERANCE);
        assertEquals(0, WispFlights.alongFlight(GLOVE, HOME, 1f).distanceTo(HOME), TOLERANCE);
    }

    @Test
    void theFlightIsFastOffTheGloveAndSettlesIntoTheCell() {
        double way = GLOVE.distanceTo(HOME);
        double halfway = WispFlights.alongFlight(GLOVE, HOME, 0.5f).distanceTo(GLOVE);
        assertTrue(halfway > way * 0.8, "Half the flight should cover most of the way, covered " + halfway / way);
        double before = 0;
        for (int tenth = 1; tenth <= 10; tenth++) {
            double covered = WispFlights.alongFlight(GLOVE, HOME, tenth / 10f).distanceTo(GLOVE);
            assertTrue(covered > before, "The wisp should move on every tenth, not on " + tenth);
            before = covered;
        }
    }

    @Test
    void aWispWithNoFlightStandsAtItsCell() {
        BlockPos cell = new BlockPos(7, 64, -3);
        assertEquals(Vec3.atCenterOf(cell), WispFlights.centerOf(cell, 100f));
    }
}

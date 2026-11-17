package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.FallingDripPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The falling drip a client launches from a fall's payload: it leaves the
 * start point from rest, falling under the drip's gravity as the server's
 * fall reckons, and lives out the fall's ticks, so it reaches the landing
 * on the fall's last tick and splats there (decision goo-moving-downward-falls-as-drips).
 */
class FallingDripLaunchTest {

    private static final FallingDripPayload FALL = new FallingDripPayload(new Vec3(12.5, 70.25, -3.5), 66.0667, 23,
            0x51A03E, 0xC9FECE, false);
    /** A wall run's drip: one cell down the east wall over a pour pace of three ticks. */
    private static final int POUR_PACE = 3;
    private static final FallingDripPayload WALL_RUN = new FallingDripPayload(new Vec3(12.99375, 71, -3.5), 70,
            POUR_PACE, 0x51A03E, 0xC9FECE, true);

    /**
     * A wall run's drip moves straight down the wall at the run's pace,
     * covering one cell in the pour pace's ticks.
     */
    @Test
    void aWallRunsDripMovesStraightDownOneCellInThePourPace() {
        FallingDripLaunch launch = FallingDripLaunch.of(WALL_RUN);

        assertEquals(WALL_RUN.start(), launch.start());
        assertEquals(0, launch.velocity().x);
        assertEquals(0, launch.velocity().z);
        assertEquals(-1.0, launch.velocity().y * POUR_PACE, 1e-9);
    }

    @Test
    void theDripStartsAtTheFallsStartPoint() {
        assertEquals(FALL.start(), FallingDripLaunch.of(FALL).start());
    }

    @Test
    void theDripLeavesFromRest() {
        assertEquals(Vec3.ZERO, FallingDripLaunch.of(FALL).velocity());
    }

    @Test
    void theDripLivesUntilItSplatsOnTheFallsLastTick() {
        assertEquals(FALL.ticks() + FallingDripLaunch.SPLAT_TICK, FallingDripLaunch.of(FALL).lifetime());
    }
}

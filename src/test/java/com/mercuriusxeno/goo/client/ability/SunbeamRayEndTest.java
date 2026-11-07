package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The local caster's sunbeam ray, drawn frame by frame against a wall while
 * the server's rays arrive once a tick a round trip late, holds still under
 * a steady look and moves by a bounded step under a turning one
 * (decision diagnose-and-correct-the-sunbeam-jitter).
 */
class SunbeamRayEndTest {

    private static final double RANGE = 32;
    private static final double WALL_Z = 20;
    private static final Vec3 EYE = new Vec3(0, 64, 0);
    private static final int FRAMES_PER_TICK = 3;
    private static final int ROUND_TRIP_TICKS = 2;
    private static final int FRAMES = 60;
    private static final double TURN_DEGREES_PER_FRAME = 0.5;
    /** A frame's step may exceed the look's own sweep across the wall by this share before it reads as a jump. */
    private static final double STEP_SLACK = 1.5;
    private static final double STILL = 1e-9;

    /** A wall across the look at {@link #WALL_Z}, stopping any line that reaches it. */
    private static final SunbeamRayEnd.SightLine WALL = (from, reach) -> {
        if (reach.z < WALL_Z) {
            return reach;
        }
        double along = (WALL_Z - from.z) / (reach.z - from.z);
        return from.add(reach.subtract(from).scale(along));
    };

    private static Vec3 lookAtYaw(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec3(Math.sin(radians), 0, Math.cos(radians));
    }

    private static Vec3 wallPointAtYaw(double degrees) {
        return WALL.stop(EYE, EYE.add(lookAtYaw(degrees).scale(RANGE)));
    }

    /** The ray's drawn end each frame, the look's yaw on each frame given by the turn per frame. */
    private static List<Vec3> drawnEnds(double turnPerFrame) {
        List<Vec3> ends = new ArrayList<>(FRAMES);
        for (int frame = 0; frame < FRAMES; frame++) {
            int sampledTick = Math.max(0, frame / FRAMES_PER_TICK - ROUND_TRIP_TICKS);
            Vec3 serverEnd = wallPointAtYaw(sampledTick * FRAMES_PER_TICK * turnPerFrame);
            SunbeamRayEnd.OwnAim own = new SunbeamRayEnd.OwnAim(EYE, wallPointAtYaw(frame * turnPerFrame));
            ends.add(SunbeamRayEnd.drawnEnd(serverEnd, own, RANGE, WALL));
        }
        return ends;
    }

    @Test
    void aSteadyLookHoldsTheEndStill() {
        List<Vec3> ends = drawnEnds(0);
        assertAll(ends.stream().map(end -> () -> assertEquals(0, end.distanceTo(ends.getFirst()), STILL)));
    }

    @Test
    void aTurningLookMovesTheEndByABoundedStepEachFrame() {
        List<Vec3> ends = drawnEnds(TURN_DEGREES_PER_FRAME);
        double lookSweep = wallPointAtYaw(TURN_DEGREES_PER_FRAME).distanceTo(wallPointAtYaw(0));
        assertAll(java.util.stream.IntStream.range(1, FRAMES).mapToObj(frame -> () -> {
            double step = ends.get(frame).distanceTo(ends.get(frame - 1));
            assertTrue(step <= lookSweep * STEP_SLACK,
                    "frame " + frame + " stepped " + step + " against a look sweep of " + lookSweep);
        }));
    }
}

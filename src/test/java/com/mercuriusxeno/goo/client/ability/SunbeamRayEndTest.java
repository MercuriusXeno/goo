package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The local caster's sunbeam ray, drawn frame by frame against a wall while
 * the server's rays arrive once a tick a round trip late, holds still under
 * a steady look and moves by a bounded step under a turning one
 * (decision diagnose-and-correct-the-sunbeam-jitter); a ray striking a
 * prism that refracts it runs into the prism's middle, where its core is
 * (decision diagnose-and-correct-the-sunbeam-prism-depth); a ray whose hit
 * is a block holding a prism ends in that prism
 * (decision reflector-rail-blooms-out-inside-the-prism).
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

    /** A prism in the wall's cell straight ahead, the ray striking its near face. */
    private static final BlockPos PRISM = new BlockPos(0, 64, 20);

    @Test
    void aRayStrikingARefractingPrismGathersAtItsMiddle() {
        Vec3 struck = new Vec3(0.5, 64.5, WALL_Z + 0.25);
        assertEquals(Vec3.atCenterOf(PRISM), SunbeamRayEnd.gathersAt(EYE.add(0.5, 0.5, 0), struck, PRISM::equals));
    }

    @Test
    void aRayStrikingAnythingElseGathersWhereItStruck() {
        Vec3 struck = new Vec3(0.5, 64.5, WALL_Z);
        assertEquals(struck, SunbeamRayEnd.gathersAt(EYE.add(0.5, 0.5, 0), struck, pos -> false));
    }

    @Nested
    class EndsInPrism {

        private final BlockHitResult blockHit = mock(BlockHitResult.class);

        EndsInPrism() {
            when(blockHit.getType()).thenReturn(HitResult.Type.BLOCK);
            when(blockHit.getBlockPos()).thenReturn(PRISM);
        }

        @Test
        void aBlockHitOnAPrismEndsInAPrism() {
            PrismBlockEntity prism = mock(PrismBlockEntity.class);
            assertTrue(SunbeamRayEnd.endsInPrism(blockHit, pos -> PRISM.equals(pos) ? prism : null));
        }

        @Test
        void aBlockHitOnAnyOtherBlockEndsNowhereSpecial() {
            assertFalse(SunbeamRayEnd.endsInPrism(blockHit, pos -> null));
        }

        @Test
        void anEntityHitEndsNowhereSpecial() {
            EntityHitResult entityHit = mock(EntityHitResult.class);
            when(entityHit.getType()).thenReturn(HitResult.Type.ENTITY);
            PrismBlockEntity prism = mock(PrismBlockEntity.class);
            assertFalse(SunbeamRayEnd.endsInPrism(entityHit, pos -> prism));
        }

        @Test
        void aMissEndsNowhereSpecial() {
            when(blockHit.getType()).thenReturn(HitResult.Type.MISS);
            PrismBlockEntity prism = mock(PrismBlockEntity.class);
            assertFalse(SunbeamRayEnd.endsInPrism(blockHit, pos -> prism));
        }
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

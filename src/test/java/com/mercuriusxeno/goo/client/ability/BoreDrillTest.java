package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drawn drill extends from the lifted blob by the glove, in whichever
 * hand holds it, to its point: where the sight meets the first breakable
 * block within reach, or the end of reach where it meets none. It narrows
 * from a wide mouth at the blob to the point radius Bore breaks with, never
 * wider than a block
 * (decisions bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand,
 * bore-drill-point-floats-along-the-crosshair-to-the-aimed-block, bore-shows-a-big-drill-from-the-glove).
 */
class BoreDrillTest {

    /** A survival player's block reach. */
    private static final double REACH = 4.5;
    /** rock_bore.json's point radius. */
    private static final double POINT_RADIUS = 0.1;
    private static final double SLACK = 1e-9;
    private static final double HALF_A_BLOCK = 0.5;
    /** An eye in the middle of a block's face height, looking along +Z, level. */
    private static final Vec3 EYE = new Vec3(3.5, 70.5, -8.5);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final CameraEye.Sight SIGHT = new CameraEye.Sight(EYE, LOOK);
    /** Yaw 0 looks along +Z. */
    private static final float YAW = 0f;
    /** Where the lifted blob stands by a glove in the right hand, and by one in the left: right is -X facing +Z. */
    private static final Vec3 RIGHT_HAND_BLOB = EYE.add(-0.4, -0.35, 0.6);
    private static final Vec3 LEFT_HAND_BLOB = EYE.add(0.4, -0.35, 0.6);
    private static final int SAMPLES = 64;
    private static final Predicate<BlockPos> NOTHING = cell -> false;

    private static Vec3 farEndOf(ConeSections.Volume volume) {
        return volume.apex().add(volume.axis().scale(volume.range()));
    }

    @Nested
    class TheMouth {

        @Test
        void itStandsAtTheBlobByAGloveInTheRightHand() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, NOTHING.negate(), NOTHING);
            assertEquals(RIGHT_HAND_BLOB, BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS).apex());
        }

        @Test
        void itStandsAtTheBlobByAGloveInTheLeftHand() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, NOTHING.negate(), NOTHING);
            assertEquals(LEFT_HAND_BLOB, BoreDrill.volume(LEFT_HAND_BLOB, point, YAW, POINT_RADIUS).apex());
        }
    }

    @Nested
    class ThePoint {

        @Test
        void itStandsWhereTheSightMeetsABreakableBlockTwoBlocksOut() {
            BlockPos stone = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, cell -> !cell.equals(stone), stone::equals);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(0, farEndOf(drill).distanceTo(new Vec3(EYE.x, EYE.y, stone.getZ())), 1e-6);
        }

        @Test
        void itStandsAtTheEndOfReachWhereTheSightMeetsNoBreakableBlock() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, NOTHING.negate(), NOTHING);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(0, farEndOf(drill).distanceTo(EYE.add(LOOK.scale(REACH))), 1e-6);
        }
    }

    /** The vortex builds up when the bore starts and disperses after it ends, rather than switching. */
    @Nested
    class Presence {

        @Test
        void itBuildsUpOverTheFadeInWhileHeld() {
            assertEquals(0.5, BoreDrill.presenceAfter(0, true, BoreDrill.FADE_IN_TICKS / 2), SLACK);
            assertEquals(1, BoreDrill.presenceAfter(0.5, true, BoreDrill.FADE_IN_TICKS), SLACK);
        }

        @Test
        void itDispersesOverTheFadeOutOnceReleased() {
            assertEquals(0.5, BoreDrill.presenceAfter(1, false, BoreDrill.FADE_OUT_TICKS / 2), SLACK);
            assertEquals(0, BoreDrill.presenceAfter(0.5, false, BoreDrill.FADE_OUT_TICKS), SLACK);
        }
    }

    @Nested
    class Radius {

        @Test
        void itNarrowsFromItsMouthAtTheBlobToThePointRadiusAndIsNeverWiderThanABlock() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, NOTHING.negate(), NOTHING);
            ConeSections.Volume drill = BoreDrill.volume(RIGHT_HAND_BLOB, point, YAW, POINT_RADIUS);
            assertEquals(POINT_RADIUS, drill.radiusAt().applyAsDouble(drill.range()), SLACK);
            double previous = Double.MAX_VALUE;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double distance = drill.range() * sample / SAMPLES;
                double radius = drill.radiusAt().applyAsDouble(distance);
                assertTrue(radius <= previous, "the drill widens to " + radius + " at " + distance);
                assertTrue(radius <= HALF_A_BLOCK, "the drill is " + 2 * radius + " wide at " + distance);
                previous = radius;
            }
            assertTrue(drill.radiusAt().applyAsDouble(0) > POINT_RADIUS, "the drill's mouth is no wider than its point");
        }
    }
}

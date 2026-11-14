package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.BoreDrillShape;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bore's drawn drill is the cone the break reads: it extends from Bore's
 * blob, wherever it has eased to, past the cross section's center on the
 * crosshair's plane to its tip, the past length beyond; as wide at the blob
 * as the step's width and tapering to a true point, so its section on the
 * plane is the ellipse the break picks with
 * (decisions bore-drill-dimensions-drive-the-visuals-and-the-break,
 * bore-cross-section-is-where-the-angled-drill-meets-the-look-plane,
 * bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand,
 * bore-drill-point-floats-along-the-crosshair-to-the-aimed-block, bore-shows-a-big-drill-from-the-glove).
 */
class BoreDrillTest {

    /** A survival player's block reach. */
    private static final double REACH = 4.5;
    private static final BoreDrillShape SHIPPED = BoreDrillShape.SHIPPED;
    private static final double SLACK = 1e-9;
    private static final double HALF_A_BLOCK = 0.5;
    /** An eye in the middle of a block's face height, looking along +Z, level. */
    private static final Vec3 EYE = new Vec3(3.5, 70.5, -8.5);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    private static final CameraEye.Sight SIGHT = new CameraEye.Sight(EYE, LOOK);
    /** Yaw 0 looks along +Z. */
    private static final float YAW = 0f;
    /** Where the lifted blob rests by a glove in the right hand: right is -X facing +Z. */
    private static final Vec3 RIGHT_HAND_REST = SpraySource.inView(EYE, 0f, YAW, true);
    private static final int SAMPLES = 64;
    private static final Predicate<BlockPos> NOTHING = cell -> false;

    private static Vec3 farEndOf(ConeSections.Volume volume) {
        return volume.apex().add(volume.axis().scale(volume.range()));
    }

    /** The drill held on a depth point as the blob eases it: from the blob's place to its tip. */
    private static ConeSections.Volume heldOn(Vec3 point, BoreDrillShape shape) {
        Vec3 place = BoreBlobPlace.placeFor(EYE, LOOK, point, RIGHT_HAND_REST, shape);
        return BoreDrill.volume(place, point, shape, YAW);
    }

    /** The drill's radius where its axis crosses the crosshair's plane, at the depth point. */
    private static double radiusAtThePlane(ConeSections.Volume drill, Vec3 point) {
        return drill.radiusAt().applyAsDouble(point.distanceTo(drill.apex()));
    }

    /**
     * The drill extends from the blob wherever it has eased to: its mouth at
     * the blob's place and its far end at the tip, the past length beyond the
     * depth point (decisions bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand,
     * bore-blob-eases-to-place-the-drill-at-the-crosshair).
     */
    @Nested
    class TheMouth {

        @Test
        void itStandsAtTheBlobsEasedPlaceAndItsFarEndAtTheTipPastThePoint() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, SHIPPED, true, NOTHING.negate(), NOTHING);
            Vec3 eased = BoreBlobPlace.placeFor(EYE, LOOK, point, RIGHT_HAND_REST, SHIPPED);
            ConeSections.Volume drill = BoreDrill.volume(eased, point, SHIPPED, YAW);
            assertTrue(eased.distanceTo(RIGHT_HAND_REST) > 0.5, "the place stands at the glove's rest");
            assertEquals(eased, drill.apex());
            assertEquals(0, farEndOf(drill).distanceTo(point.add(drill.axis().scale(SHIPPED.pastLength()))), 1e-6);
        }
    }

    /** The point seam answers the cross section's center on the sight line, which the drill passes through. */
    @Nested
    class ThePoint {

        @Test
        void itStandsWhereTheSightMeetsABreakableBlockTwoBlocksOut() {
            BlockPos stone = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, SHIPPED, true, cell -> !cell.equals(stone), stone::equals);
            assertEquals(0, point.distanceTo(new Vec3(EYE.x, EYE.y, stone.getZ())), 1e-6);
        }

        @Test
        void itStandsAtTheFaceOfASolidBlockOutsideTheTagTwoBlocksOut() {
            BlockPos bedrock = BlockPos.containing(EYE.add(LOOK.scale(2.5)));
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, SHIPPED, true, cell -> !cell.equals(bedrock), NOTHING);
            assertEquals(0, point.distanceTo(new Vec3(EYE.x, EYE.y, bedrock.getZ())), 1e-6);
        }

        @Test
        void itStandsAtTheEndOfReachWhereTheSightMeetsNoBlock() {
            Vec3 point = BoreDrill.pointOf(SIGHT, REACH, SHIPPED, true, NOTHING.negate(), NOTHING);
            assertEquals(0, point.distanceTo(EYE.add(LOOK.scale(REACH))), 1e-6);
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

    /**
     * The drill is as wide at the blob as the step's width, narrows to a true
     * point at its tip, and where it crosses the crosshair's plane its section
     * is the ellipse the break picks with
     * (decision bore-drill-dimensions-drive-the-visuals-and-the-break).
     */
    @Nested
    class Radius {

        /**
         * The cone's square section where its axis crosses the plane, taper times past length, against the
         * slanted ellipse's short half, that over the root of one less the squared taper and tangent: under
         * one percent apart for any drill never wider than a block held under 45 degrees.
         */
        private static final double SECTION_SHARE = 0.01;

        private void assertTheConeTheBreakReads(BoreDrillShape shape) {
            Vec3 point = EYE.add(LOOK.scale(4));
            ConeSections.Volume drill = heldOn(point, shape);
            assertEquals(shape.width() / 2, drill.radiusAt().applyAsDouble(0), 1e-6);
            assertEquals(0, drill.radiusAt().applyAsDouble(drill.range()), 1e-6);
            assertEquals(shape.shortHalf(), radiusAtThePlane(drill, point), shape.shortHalf() * SECTION_SHARE);
            double previous = Double.MAX_VALUE;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double distance = drill.range() * sample / SAMPLES;
                double radius = drill.radiusAt().applyAsDouble(distance);
                assertTrue(radius <= previous, "the drill widens to " + radius + " at " + distance);
                assertTrue(radius <= HALF_A_BLOCK, "the drill is " + 2 * radius + " wide at " + distance);
                previous = radius;
            }
        }

        @Test
        void theShippedDrillIsTheConeTheBreakReads() {
            assertTheConeTheBreakReads(SHIPPED);
        }

        @Test
        void aWiderDrillReachingFartherPastIsTheConeItsBreakReads() {
            assertTheConeTheBreakReads(new BoreDrillShape(1, 2, 22.5, 1));
        }

        @Test
        void aNearDrillShortensFromTheBlobAndKeepsItsSectionOnThePlane() {
            Vec3 near = EYE.add(LOOK.scale(1.2));
            Vec3 far = EYE.add(LOOK.scale(4));
            ConeSections.Volume nearDrill = heldOn(near, SHIPPED);
            assertTrue(nearDrill.range() < SHIPPED.length(), "the near drill runs " + nearDrill.range() + " long");
            assertEquals(radiusAtThePlane(heldOn(far, SHIPPED), far), radiusAtThePlane(nearDrill, near), 1e-6);
        }
    }
}

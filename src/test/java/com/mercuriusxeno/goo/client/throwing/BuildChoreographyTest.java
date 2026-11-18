package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.BuildBounds;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Build cast submits only on its second right click, sized by the drag and raised by the pitch (decision spire-rips-walls-and-platforms). */
class BuildChoreographyTest {

    private static final BlockPos CORNER = new BlockPos(10, 64, 10);
    /** An eye three blocks above the corner's top face, two blocks west of the corner. */
    private static final Vec3 EYE = new Vec3(8.5, 68, 10.5);
    private static final float LEVEL_PITCH = 30f;
    private static final BuildBounds BOUNDS = new BuildBounds(8, 8);

    /** A look from the eye down onto the top face of the ground cell at an x, z. */
    private static Vec3 lookAt(double x, double z) {
        return new Vec3(x, CORNER.getY() + 1.0, z).subtract(EYE).normalize();
    }

    /** Pins the corner, drags to the cell two east, and releases. */
    private static BuildChoreography sizedAndReleased() {
        BuildChoreography cast = new BuildChoreography();
        cast.press(CORNER, Direction.UP, BOUNDS);
        cast.tick(true, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
        cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
        return cast;
    }

    @Nested
    class Submitting {

        @Test
        void theSecondPressSubmitsTheDraggedFootprintAtThePitchedRise() {
            BuildChoreography cast = sizedAndReleased();
            cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH - 3 * BuildChoreography.DEGREES_PER_BLOCK);

            Optional<BuildFootprint> submitted = cast.press(CORNER, Direction.UP, BOUNDS);

            assertEquals(Optional.of(new BuildFootprint(CORNER, new BlockPos(12, 64, 10), 4)), submitted);
            assertEquals(BuildChoreography.Phase.IDLE, cast.phase());
        }

        @Test
        void pressesWhileTheDragHoldsSubmitNothing() {
            BuildChoreography cast = new BuildChoreography();
            cast.press(CORNER, Direction.UP, BOUNDS);
            cast.tick(true, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);

            assertEquals(Optional.empty(), cast.press(CORNER, Direction.UP, BOUNDS));
            assertEquals(BuildChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void theHeldKeyAfterASubmitPinsNoNewCorner() {
            BuildChoreography cast = sizedAndReleased();
            cast.press(CORNER, Direction.UP, BOUNDS);

            cast.press(CORNER, Direction.UP, BOUNDS);
            assertEquals(BuildChoreography.Phase.IDLE, cast.phase());

            cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
            cast.press(CORNER, Direction.UP, BOUNDS);
            assertEquals(BuildChoreography.Phase.SIZING, cast.phase());
        }
    }

    @Nested
    class Abandoning {

        @Test
        void aReleaseLeftUnsubmittedSendsNothingWhenCancelled() {
            BuildChoreography cast = sizedAndReleased();
            assertEquals(BuildChoreography.Phase.RISING, cast.phase());

            cast.cancel();

            assertEquals(Optional.empty(), cast.footprint());
            assertEquals(Optional.empty(), cast.press(CORNER, Direction.UP, BOUNDS));
            assertEquals(BuildChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void aPressOnNoGroundPinsNothing() {
            BuildChoreography cast = new BuildChoreography();

            cast.press(null, Direction.UP, BOUNDS);

            assertEquals(BuildChoreography.Phase.IDLE, cast.phase());
            assertEquals(Optional.empty(), cast.footprint());
        }
    }

    @Nested
    class Rising {

        @Test
        void thePitchAtReleaseRisesTheLowest() {
            assertEquals(BuildBounds.MIN_RISE, BuildChoreography.riseFromPitch(LEVEL_PITCH, LEVEL_PITCH, BOUNDS));
        }

        @Test
        void eachStepUpRisesOneBlock() {
            assertEquals(3, BuildChoreography.riseFromPitch(LEVEL_PITCH,
                    LEVEL_PITCH - 2 * BuildChoreography.DEGREES_PER_BLOCK, BOUNDS));
        }

        @Test
        void theRiseHoldsWithinItsCaps() {
            assertEquals(BOUNDS.maxRise(), BuildChoreography.riseFromPitch(LEVEL_PITCH, -90f, BOUNDS));
            assertEquals(BuildBounds.MIN_RISE, BuildChoreography.riseFromPitch(LEVEL_PITCH, 90f, BOUNDS));
        }
    }

    /**
     * A footprint dragged up a wall's edge can still be pushed out sideways as far as the bounds allow.
     * decision build-goes-sideways
     */
    @Nested
    class Sideways {

        private static final BlockPos EDGE_BOTTOM = new BlockPos(10, 64, 0);
        private static final BlockPos EDGE_TOP = new BlockPos(10, 79, 0);
        /** A player standing on the ground east and south of the edge. */
        private final Vec3 eye = new Vec3(14.5, 65.62, 5.5);

        private Vec3 lookAtEastFaceOf(BlockPos cell) {
            return new Vec3(cell.getX() + 1.0, cell.getY() + 0.5, cell.getZ() + 0.5).subtract(eye).normalize();
        }

        private static float pitchOf(Vec3 look) {
            return (float) -Math.toDegrees(Math.asin(look.y));
        }

        /** The edge's middle on its east face, where the push runs out from. */
        private static final Vec3 EDGE_MIDDLE = new Vec3(11.0, 72.0, 0.5);

        private BuildChoreography draggedUpTheEdge() {
            BuildChoreography cast = new BuildChoreography();
            cast.press(EDGE_BOTTOM, Direction.EAST, new BuildBounds(16, 16));
            Vec3 top = lookAtEastFaceOf(EDGE_TOP);
            cast.tick(true, eye, top, pitchOf(top));
            cast.tick(false, eye, top, pitchOf(top));
            return cast;
        }

        /** The rise after looking at the point some blocks out from the edge's middle, along its normal. */
        private int riseLookingOut(BuildChoreography cast, double out) {
            Vec3 look = EDGE_MIDDLE.add(out, 0, 0).subtract(eye).normalize();
            cast.tick(false, eye, look, pitchOf(look));
            return cast.footprint().orElseThrow().rise();
        }

        @Test
        void anEdgeDraggedUpItsHeightPushesOutToWhereTheLookAims() {
            BuildChoreography cast = draggedUpTheEdge();

            assertEquals(EDGE_TOP, cast.footprint().orElseThrow().opposite());
            assertEquals(16, riseLookingOut(cast, 16));
            assertEquals(5, riseLookingOut(cast, 5));
            assertEquals(16, riseLookingOut(cast, 30), "held within the bounds");
        }
    }

    @Nested
    class Dragging {

        @Test
        void theLookFindsTheGroundCellOnTheCornersLevel() {
            assertEquals(Optional.of(new BlockPos(13, 64, 9)),
                    BuildChoreography.cellUnderLook(CORNER, Direction.UP, EYE, lookAt(13.5, 9.5)));
        }

        /** decision build-goes-sideways */
        @Test
        void theLookFindsTheCellInAWallsPlane() {
            Vec3 eye = new Vec3(10.5, 65.5, 6.5);
            Vec3 look = new Vec3(12.5, 66.5, CORNER.getZ()).subtract(eye).normalize();

            assertEquals(Optional.of(new BlockPos(12, 66, CORNER.getZ())),
                    BuildChoreography.cellUnderLook(CORNER, Direction.NORTH, eye, look));
            assertTrue(BuildChoreography.cellUnderLook(CORNER, Direction.NORTH, eye, look.reverse()).isEmpty());
        }

        @Test
        void aLookAboveTheHorizonFindsNoGround() {
            assertTrue(BuildChoreography.cellUnderLook(CORNER, Direction.UP, EYE, new Vec3(1, 0.2, 0).normalize()).isEmpty());
        }
    }
}

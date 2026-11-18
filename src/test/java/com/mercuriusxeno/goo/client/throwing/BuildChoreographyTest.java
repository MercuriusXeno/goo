package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.BuildBounds;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import net.minecraft.core.BlockPos;
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
        cast.press(CORNER, BOUNDS);
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

            Optional<BuildFootprint> submitted = cast.press(CORNER, BOUNDS);

            assertEquals(Optional.of(new BuildFootprint(CORNER, new BlockPos(12, 64, 10), 4)), submitted);
            assertEquals(BuildChoreography.Phase.IDLE, cast.phase());
        }

        @Test
        void pressesWhileTheDragHoldsSubmitNothing() {
            BuildChoreography cast = new BuildChoreography();
            cast.press(CORNER, BOUNDS);
            cast.tick(true, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);

            assertEquals(Optional.empty(), cast.press(CORNER, BOUNDS));
            assertEquals(BuildChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void theHeldKeyAfterASubmitPinsNoNewCorner() {
            BuildChoreography cast = sizedAndReleased();
            cast.press(CORNER, BOUNDS);

            cast.press(CORNER, BOUNDS);
            assertEquals(BuildChoreography.Phase.IDLE, cast.phase());

            cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
            cast.press(CORNER, BOUNDS);
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
            assertEquals(Optional.empty(), cast.press(CORNER, BOUNDS));
            assertEquals(BuildChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void aPressOnNoGroundPinsNothing() {
            BuildChoreography cast = new BuildChoreography();

            cast.press(null, BOUNDS);

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

    @Nested
    class Dragging {

        @Test
        void theLookFindsTheGroundCellOnTheCornersLevel() {
            assertEquals(Optional.of(new BlockPos(13, 64, 9)),
                    BuildChoreography.groundCellUnderLook(CORNER, EYE, lookAt(13.5, 9.5)));
        }

        @Test
        void aLookAboveTheHorizonFindsNoGround() {
            assertTrue(BuildChoreography.groundCellUnderLook(CORNER, EYE, new Vec3(1, 0.2, 0).normalize()).isEmpty());
        }
    }
}

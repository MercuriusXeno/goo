package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Spire cast submits only on its second right click, sized by the drag and raised by the pitch (decision spire-rips-walls-and-platforms). */
class SpireChoreographyTest {

    private static final BlockPos CORNER = new BlockPos(10, 64, 10);
    /** An eye three blocks above the corner's top face, two blocks west of the corner. */
    private static final Vec3 EYE = new Vec3(8.5, 68, 10.5);
    private static final float LEVEL_PITCH = 30f;

    /** A look from the eye down onto the top face of the ground cell at an x, z. */
    private static Vec3 lookAt(double x, double z) {
        return new Vec3(x, CORNER.getY() + 1.0, z).subtract(EYE).normalize();
    }

    /** Pins the corner, drags to the cell two east, and releases. */
    private static SpireChoreography sizedAndReleased() {
        SpireChoreography cast = new SpireChoreography();
        cast.press(CORNER);
        cast.tick(true, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
        cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
        return cast;
    }

    @Nested
    class Submitting {

        @Test
        void theSecondPressSubmitsTheDraggedFootprintAtThePitchedRise() {
            SpireChoreography cast = sizedAndReleased();
            cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH - 3 * SpireChoreography.DEGREES_PER_BLOCK);

            Optional<SpireFootprint> submitted = cast.press(CORNER);

            assertEquals(Optional.of(new SpireFootprint(CORNER, new BlockPos(12, 64, 10), 4)), submitted);
            assertEquals(SpireChoreography.Phase.IDLE, cast.phase());
        }

        @Test
        void pressesWhileTheDragHoldsSubmitNothing() {
            SpireChoreography cast = new SpireChoreography();
            cast.press(CORNER);
            cast.tick(true, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);

            assertEquals(Optional.empty(), cast.press(CORNER));
            assertEquals(SpireChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void theHeldKeyAfterASubmitPinsNoNewCorner() {
            SpireChoreography cast = sizedAndReleased();
            cast.press(CORNER);

            cast.press(CORNER);
            assertEquals(SpireChoreography.Phase.IDLE, cast.phase());

            cast.tick(false, EYE, lookAt(12.5, 10.5), LEVEL_PITCH);
            cast.press(CORNER);
            assertEquals(SpireChoreography.Phase.SIZING, cast.phase());
        }
    }

    @Nested
    class Abandoning {

        @Test
        void aReleaseLeftUnsubmittedSendsNothingWhenCancelled() {
            SpireChoreography cast = sizedAndReleased();
            assertEquals(SpireChoreography.Phase.RISING, cast.phase());

            cast.cancel();

            assertEquals(Optional.empty(), cast.footprint());
            assertEquals(Optional.empty(), cast.press(CORNER));
            assertEquals(SpireChoreography.Phase.SIZING, cast.phase());
        }

        @Test
        void aPressOnNoGroundPinsNothing() {
            SpireChoreography cast = new SpireChoreography();

            cast.press(null);

            assertEquals(SpireChoreography.Phase.IDLE, cast.phase());
            assertEquals(Optional.empty(), cast.footprint());
        }
    }

    @Nested
    class Rising {

        @Test
        void thePitchAtReleaseRisesTheLowest() {
            assertEquals(SpireFootprint.MIN_RISE, SpireChoreography.riseFromPitch(LEVEL_PITCH, LEVEL_PITCH));
        }

        @Test
        void eachStepUpRisesOneBlock() {
            assertEquals(3, SpireChoreography.riseFromPitch(LEVEL_PITCH,
                    LEVEL_PITCH - 2 * SpireChoreography.DEGREES_PER_BLOCK));
        }

        @Test
        void theRiseHoldsWithinItsCaps() {
            assertEquals(SpireFootprint.MAX_RISE, SpireChoreography.riseFromPitch(LEVEL_PITCH, -90f));
            assertEquals(SpireFootprint.MIN_RISE, SpireChoreography.riseFromPitch(LEVEL_PITCH, 90f));
        }
    }

    @Nested
    class Dragging {

        @Test
        void theLookFindsTheGroundCellOnTheCornersLevel() {
            assertEquals(Optional.of(new BlockPos(13, 64, 9)),
                    SpireChoreography.groundCellUnderLook(CORNER, EYE, lookAt(13.5, 9.5)));
        }

        @Test
        void aLookAboveTheHorizonFindsNoGround() {
            assertTrue(SpireChoreography.groundCellUnderLook(CORNER, EYE, new Vec3(1, 0.2, 0).normalize()).isEmpty());
        }
    }
}

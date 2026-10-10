package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The oculus's column becomes the eye once, the eye hovers low off its face,
 * and its lids open only while the viewer looks at it
 * (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusStyleTest {

    private static final float EPSILON = 1e-4f;
    private static final long SINCE = 1000L;

    @Nested
    class Transformation {

        @Test
        void runsFromTheComboAndHoldsWhole() {
            assertEquals(0f, OculusStyle.transformationShare(SINCE, SINCE), EPSILON);
            assertEquals(0.5f, OculusStyle.transformationShare(SINCE + OculusStyle.TRANSFORM_TICKS / 2, SINCE),
                    EPSILON);
            assertEquals(1f, OculusStyle.transformationShare(SINCE + 10 * OculusStyle.TRANSFORM_TICKS, SINCE),
                    EPSILON);
        }

        @Test
        void theEyeStartsStillAsItForms() {
            assertEquals(0f, OculusStyle.hover(0f), EPSILON);
        }
    }

    @Nested
    class Placement {

        @Test
        void aFloorEyeHoversLowOffTheFloor() {
            Vec3 eye = OculusStyle.eyeInCell(Direction.UP);
            assertEquals(new Vec3(0.5, 3.5 / 16, 0.5), eye);
        }

        @Test
        void aWallEyeHoversOffTheWall() {
            Vec3 eye = OculusStyle.eyeInCell(Direction.SOUTH);
            assertEquals(3.5 / 16, eye.z, EPSILON);
        }
    }

    @Nested
    class Lids {

        private final Vec3 camera = new Vec3(0.5, 64, 0.5);
        private final Vec3 eye = new Vec3(0.5, 64, 10.5);

        @Test
        void aLookOnTheEyeIsLookingAtIt() {
            assertTrue(OculusLids.lookedAt(camera, new Vec3(0, 0, 1), eye));
        }

        @Test
        void aLookAwayIsNot() {
            assertFalse(OculusLids.lookedAt(camera, new Vec3(1, 0, 0), eye));
        }

        @Test
        void theLidsStayShutUntilLookedAt() {
            BlockPos cell = new BlockPos(0, 64, 10);
            assertEquals(1f, OculusLids.closure(cell, false, SINCE), EPSILON);
            assertEquals(1f, OculusLids.closure(cell, false, SINCE + 20), EPSILON);
        }

        @Test
        void theLidsOpenOverAFewTicksWhileLookedAt() {
            BlockPos cell = new BlockPos(5, 64, 10);
            OculusLids.closure(cell, true, SINCE + OculusLids.BLINK_TICKS);
            assertEquals(0f, OculusLids.closure(cell, true, SINCE + OculusLids.BLINK_TICKS + 20), EPSILON);
        }

        @Test
        void theLidsCloseInWholePixelRows() {
            assertEquals(0, OculusStyle.lidRows(0f));
            assertEquals(OculusStyle.LID_ROWS, OculusStyle.lidRows(1f));
            assertEquals(2, OculusStyle.lidRows(0.6f));
        }

        @Test
        void anOpenEyeBlinksShutAtTheMiddleOfABlink() {
            assertEquals(1f, OculusLids.blinkClosure(OculusLids.BLINK_TICKS / 2f), EPSILON);
            assertEquals(0f, OculusLids.blinkClosure(OculusLids.BLINK_PERIOD / 2f), EPSILON);
        }
    }
}

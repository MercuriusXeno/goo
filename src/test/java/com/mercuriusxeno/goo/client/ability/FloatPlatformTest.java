package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Float's platform squares leave the feet evenly staggered, each dropping and
 * widening as it fades, and the whole platform fades over the float's last
 * ticks (decision float-blob-levitates-the-mob).
 */
class FloatPlatformTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 FEET = new Vec3(3, 70, -2);
    /** A zombie's half width. */
    private static final double HALF_WIDTH = 0.3;
    /** Ticks left well before the fade begins. */
    private static final float LONG_BEFORE_THE_END = 80f;

    @Nested
    class Drop {

        @Test
        void squaresLeaveTheFeetEvenlyStaggered() {
            double first = FloatPlatform.dropShare(7.5, 0);
            double second = FloatPlatform.dropShare(7.5, 1);
            assertEquals(1.0 / FloatPlatform.SQUARES, second - first, EPSILON);
        }

        @Test
        void aSquareStartsOverAtTheFeetOnceItReachesTheBottom() {
            assertEquals(FloatPlatform.dropShare(3, 0),
                    FloatPlatform.dropShare(3 + FloatPlatform.FLIGHT_TICKS, 0), EPSILON);
        }

        @Test
        void aSquareDropsStraightDownFromTheFeet() {
            assertEquals(FEET, FloatPlatform.squareCenter(FEET, 0));
            Vec3 bottom = FloatPlatform.squareCenter(FEET, 1);
            assertEquals(FEET.x, bottom.x, EPSILON);
            assertEquals(FEET.z, bottom.z, EPSILON);
            assertEquals(FEET.y - FloatPlatform.DROP, bottom.y, EPSILON);
        }

        @Test
        void aSquareWidensFromInsideTheFootprintToPastIt() {
            assertEquals(HALF_WIDTH * FloatPlatform.START_REACH, FloatPlatform.halfSide(0, HALF_WIDTH), EPSILON);
            assertEquals(HALF_WIDTH * FloatPlatform.END_REACH, FloatPlatform.halfSide(1, HALF_WIDTH), EPSILON);
            assertTrue(FloatPlatform.halfSide(0, HALF_WIDTH) < HALF_WIDTH);
            assertTrue(FloatPlatform.halfSide(1, HALF_WIDTH) > HALF_WIDTH);
        }

        @Test
        void aSquareFadesAsItDrops() {
            assertTrue(FloatPlatform.squareAlpha(0.8, LONG_BEFORE_THE_END)
                    < FloatPlatform.squareAlpha(0.3, LONG_BEFORE_THE_END));
            assertEquals(0, FloatPlatform.squareAlpha(1, LONG_BEFORE_THE_END));
        }
    }

    @Nested
    class Fade {

        @Test
        void thePlatformDimsOverTheFloatsLastTicks() {
            int full = FloatPlatform.squareAlpha(0.5, LONG_BEFORE_THE_END);
            int dimming = FloatPlatform.squareAlpha(0.5, MobAilments.FADE_TICKS / 2f);
            assertTrue(dimming > 0 && dimming < full, "full " + full + ", dimming " + dimming);
        }

        @Test
        void thePlatformIsGoneOnceTheFloatEnds() {
            assertEquals(0, FloatPlatform.squareAlpha(0.5, 0f));
        }
    }
}

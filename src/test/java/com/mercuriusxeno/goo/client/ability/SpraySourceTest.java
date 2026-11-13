package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A spray's source stands in its caster's view: 0.7 ahead of the eye along
 * the look, 0.3 out to the glove's side and 0.15 below the look, so it
 * turns with the look and mirrors for a left-hand glove.
 * goop-and-slick-spray-emanates-from-the-glove
 */
class SpraySourceTest {

    private static final Vec3 EYE = new Vec3(10, 65.62, -4);
    private static final double DELTA = 1e-6;

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, DELTA, "x");
        assertEquals(expected.y, actual.y, DELTA, "y");
        assertEquals(expected.z, actual.z, DELTA, "z");
    }

    @Test
    void aRightGloveLookingSouthSpraysAheadToTheWestAndALittleDown() {
        assertNear(EYE.add(-0.3, -0.15, 0.7), SpraySource.inView(EYE, 0f, 0f, true));
    }

    @Test
    void aLeftGloveSpraysFromTheMirroredSide() {
        assertNear(EYE.add(0.3, -0.15, 0.7), SpraySource.inView(EYE, 0f, 0f, false));
    }

    @Test
    void aRightGloveLookingEastSpraysAheadToTheSouth() {
        assertNear(EYE.add(0.7, -0.15, 0.3), SpraySource.inView(EYE, 0f, -90f, true));
    }

    @Test
    void aGloveLookingStraightDownSpraysBelowTheEyeAndBackFromTheLook() {
        assertNear(EYE.add(-0.3, -0.7, -0.15), SpraySource.inView(EYE, 90f, 0f, true));
    }
}

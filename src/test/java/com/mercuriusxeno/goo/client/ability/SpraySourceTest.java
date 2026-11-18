package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A spray's source stands in its caster's view at the default field of view
 * straight above where the glove's palm rests: 0.716 ahead of the eye along
 * the look, 0.56 out to the glove's side and 0.15 below the look, so it turns
 * with the look and mirrors for a left-hand glove.
 * palm-blob-lifts-as-the-spray-source
 */
class SpraySourceTest {

    private static final Vec3 EYE = new Vec3(10, 65.62, -4);
    private static final double DELTA = 1e-6;
    /** How far ahead of the eye the resting palm, and so the source, stands. */
    private static final double AHEAD = 0.7159375;
    /** How far out to the glove's side the resting palm, and so the source, stands. */
    private static final double SIDE = 0.56;
    /** How far below the look the source stands. */
    private static final double BELOW = 0.15;

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, DELTA, "x");
        assertEquals(expected.y, actual.y, DELTA, "y");
        assertEquals(expected.z, actual.z, DELTA, "z");
    }

    @Test
    void aRightGloveLookingSouthSpraysAheadToTheWestAndALittleDown() {
        assertNear(EYE.add(-SIDE, -BELOW, AHEAD), SpraySource.inView(EYE, 0f, 0f, true));
    }

    @Test
    void aLeftGloveSpraysFromTheMirroredSide() {
        assertNear(EYE.add(SIDE, -BELOW, AHEAD), SpraySource.inView(EYE, 0f, 0f, false));
    }

    @Test
    void aRightGloveLookingEastSpraysAheadToTheSouth() {
        assertNear(EYE.add(AHEAD, -BELOW, SIDE), SpraySource.inView(EYE, 0f, -90f, true));
    }

    @Test
    void aGloveLookingStraightDownSpraysBelowTheEyeAndBackFromTheLook() {
        assertNear(EYE.add(-SIDE, -AHEAD, -BELOW), SpraySource.inView(EYE, 90f, 0f, true));
    }

    @Test
    void theSourceStandsTheRiseStraightAboveTheRestingPalm() {
        Vec3 source = SpraySource.inView(EYE, 0f, 0f, true);
        Vec3 palm = EYE.add(-SpraySource.RESTING_PALM_IN_VIEW.x(), SpraySource.RESTING_PALM_IN_VIEW.y(),
                -SpraySource.RESTING_PALM_IN_VIEW.z());

        assertNear(palm.add(0, SpraySource.RISE_ABOVE_THE_PALM, 0), source);
    }
}

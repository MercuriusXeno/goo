package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The oculus's lids open only while the viewer looks at the eye, over a few ticks, and an open eye blinks now and
 * then (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusLidsTest {

    private static final float EPSILON = 1e-4f;
    private static final long SINCE = 1000L;
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
        int afterTheBlink = OculusLids.BLINK_SHUT_TICKS + OculusLids.BLINK_OPEN_TICKS;
        OculusLids.closure(cell, true, SINCE + afterTheBlink);
        assertEquals(0f, OculusLids.closure(cell, true, SINCE + afterTheBlink + 20), EPSILON);
    }

    /** operator ruling 2026-11-28: the lids part a lot at first and slow, and open more slowly than they close */
    @Test
    void theLidsPartMostOnTheFirstTickAndShutFasterThanTheyOpen() {
        float firstTick = OculusLids.eased(0f, 1f, 1f);
        float secondTick = OculusLids.eased(firstTick, 1f, 1f) - firstTick;
        assertTrue(firstTick > secondTick, "the first tick should part the lids most");
        assertTrue(1f - OculusLids.eased(1f, 0f, 1f) > firstTick, "a tick should shut more than it opens");
    }

    @Test
    void anOpenEyeBlinksShutThenEasesOpen() {
        assertEquals(1f, OculusLids.blinkClosure(OculusLids.BLINK_SHUT_TICKS), EPSILON);
        assertEquals(0f, OculusLids.blinkClosure(OculusLids.BLINK_PERIOD / 2f), EPSILON);
    }
}

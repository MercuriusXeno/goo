package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the aim assist that steers an item thrown roughly at the crucible's mouth into
 * its center (decision rim-and-mouth-items-slide-inward).
 */
class CrucibleAimAssistTest {

    private static final double EPSILON = 1e-6;
    private static final double CENTER = 0.5;
    /** A throw from two blocks west, a block and a half up, arcing east. */
    private static final Vec3 THROWN_FROM = new Vec3(-2.0, 1.5, CENTER);

    @Test
    void throwComingDownShortOfTheMouthIsSteeredToItsCenter() {
        Vec3 shortThrow = new Vec3(0.24, 0.15, 0.03);
        CrucibleAimAssist.Landing before = CrucibleAimAssist.landing(THROWN_FROM, shortThrow);
        assertNotNull(before);
        Vec3 aimed = CrucibleAimAssist.aimedDelta(THROWN_FROM, shortThrow);
        assertNotNull(aimed);
        CrucibleAimAssist.Landing after = CrucibleAimAssist.landing(THROWN_FROM, aimed);
        assertNotNull(after);
        assertEquals(CENTER, after.x(), EPSILON);
        assertEquals(CENTER, after.z(), EPSILON);
    }

    @Test
    void steeringKeepsTheThrowsHeightAndTiming() {
        Vec3 shortThrow = new Vec3(0.24, 0.15, 0.03);
        Vec3 aimed = CrucibleAimAssist.aimedDelta(THROWN_FROM, shortThrow);
        assertNotNull(aimed);
        assertEquals(shortThrow.y, aimed.y, EPSILON);
        assertEquals(CrucibleAimAssist.landing(THROWN_FROM, shortThrow).ticks(),
            CrucibleAimAssist.landing(THROWN_FROM, aimed).ticks());
    }

    @Test
    void throwComingDownFarFromTheMouthIsLeftAlone() {
        assertNull(CrucibleAimAssist.aimedDelta(THROWN_FROM, new Vec3(0.02, 0.15, 0.0)));
        assertNull(CrucibleAimAssist.aimedDelta(THROWN_FROM, new Vec3(0.2, 0.15, 0.2)));
    }

    @Test
    void itemBelowTheRimThatNeverRisesOverItIsLeftAlone() {
        assertNull(CrucibleAimAssist.aimedDelta(new Vec3(-1.0, 0.5, CENTER), new Vec3(0.3, 0.0, 0.0)));
    }

    @Test
    void landingFollowsItemGravityAndDrag() {
        CrucibleAimAssist.Landing drop = CrucibleAimAssist.landing(new Vec3(CENTER, 1.1, CENTER), Vec3.ZERO);
        assertNotNull(drop);
        assertEquals(2, drop.ticks());
    }
}

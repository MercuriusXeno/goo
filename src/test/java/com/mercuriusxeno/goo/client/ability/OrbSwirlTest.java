package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Orb's swirl: each arm reaches from near the core out to the rim, and
 * the arms turn with time.
 */
class OrbSwirlTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BALL = new Vec3(4, 70, -2);
    private static final double REACH = 3;

    private static double outFromTheBall(Vec3 point) {
        return Math.hypot(point.x - BALL.x, point.z - BALL.z);
    }

    @Test
    void anArmReachesFromNearTheCoreToTheRim() {
        assertEquals(REACH * OrbSwirl.CORE_SHARE, outFromTheBall(OrbSwirl.armPoint(BALL, REACH, 0, 0, 0)), EPSILON);
        assertEquals(REACH, outFromTheBall(OrbSwirl.armPoint(BALL, REACH, 0, 1, 0)), EPSILON);
    }

    @Test
    void theSwirlTurnsWithTime() {
        Vec3 now = OrbSwirl.armPoint(BALL, REACH, 1, 1, 0);
        Vec3 later = OrbSwirl.armPoint(BALL, REACH, 1, 1, 4);
        assertNotEquals(now, later);
        assertEquals(outFromTheBall(now), outFromTheBall(later), EPSILON);
    }

    @Test
    void anArmFadesTowardTheRim() {
        assertTrue((OrbSwirl.armColor(0.1) >>> 24) > (OrbSwirl.armColor(0.9) >>> 24));
    }
}

package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lift's wind leaves the prism's base close around it and spirals out around
 * it as it climbs the shaft, widening, with no corner in its path
 * (decision lift-prism-levitates-the-block-above).
 */
class LiftWindTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BASE = new Vec3(2.5, 1, 2.5);
    /** The prism's own block and a four-block shaft. */
    private static final double HEIGHT = 5;
    private static final LiftWind.Spiral SPIRAL = new LiftWind.Spiral(BASE, HEIGHT, 0.4, 1, 0f, 0L);

    @Test
    void aLineLeavesTheBaseCloseAroundThePrism() {
        Vec3 start = LiftWind.spiralPoint(SPIRAL, 0);

        assertEquals(BASE.y, start.y, EPSILON);
        assertEquals(LiftWind.START_RADIUS, Math.hypot(start.x - BASE.x, start.z - BASE.z), EPSILON);
    }

    @Test
    void aLineClimbsTheShaftAsItSpiralsOutWider() {
        Vec3 end = LiftWind.spiralPoint(SPIRAL, LiftWind.LIFE_TICKS);

        assertEquals(BASE.y + HEIGHT, end.y, EPSILON);
        assertEquals(LiftWind.END_RADIUS, Math.hypot(end.x - BASE.x, end.z - BASE.z), EPSILON);
    }

    @Test
    void aLineWindsAroundThePrismWithoutTurningACorner() {
        double mostTurn = Math.toRadians(30);
        for (int tick = 1; tick < LiftWind.LIFE_TICKS; tick++) {
            Vec3 before = LiftWind.spiralPoint(SPIRAL, tick).subtract(LiftWind.spiralPoint(SPIRAL, tick - 1.0))
                    .normalize();
            Vec3 after = LiftWind.spiralPoint(SPIRAL, tick + 1.0).subtract(LiftWind.spiralPoint(SPIRAL, tick))
                    .normalize();
            double turn = Math.acos(Math.clamp(before.dot(after), -1, 1));
            assertTrue(turn < mostTurn, "tick " + tick + " turns " + Math.toDegrees(turn));
        }
        assertTrue(LiftWind.TURNS >= 1, "it goes around the prism");
    }

    @Test
    void aLineFadesInAtTheBaseAndOutUpTheShaft() {
        assertEquals(0, LiftWind.opacity(0), EPSILON);
        assertEquals(1, LiftWind.opacity(0.5), EPSILON);
        assertEquals(0, LiftWind.opacity(1), EPSILON);
    }
}

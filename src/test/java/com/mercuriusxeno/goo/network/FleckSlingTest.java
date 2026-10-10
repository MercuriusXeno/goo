package com.mercuriusxeno.goo.network;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sweep's flecks fan evenly across the cone around the look, leaving from
 * the off-hand side to the glove side (decision shards-sling-then-morph-to-flechettes).
 */
class FleckSlingTest {

    private static final double TOLERANCE = 1e-6;
    /** Facing south. */
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    /** The right of a player facing south is west. */
    private static final Vec3 RIGHT = new Vec3(-1, 0, 0);

    @Test
    void aRightHandedSweepRunsLeftToRightAcrossTheCone() {
        List<Vec3> fan = FleckSling.fanDirections(LOOK, RIGHT, 5, 60, true);

        assertEquals(5, fan.size());
        assertEquals(-30, degreesRightOfLook(fan.getFirst()), TOLERANCE);
        assertEquals(0, degreesRightOfLook(fan.get(2)), TOLERANCE);
        assertEquals(30, degreesRightOfLook(fan.getLast()), TOLERANCE);
        for (int index = 1; index < fan.size(); index++) {
            assertTrue(degreesRightOfLook(fan.get(index)) > degreesRightOfLook(fan.get(index - 1)));
        }
    }

    @Test
    void aLeftHandedSweepRunsRightToLeft() {
        List<Vec3> fan = FleckSling.fanDirections(LOOK, RIGHT, 3, 40, false);

        assertEquals(20, degreesRightOfLook(fan.getFirst()), TOLERANCE);
        assertEquals(-20, degreesRightOfLook(fan.getLast()), TOLERANCE);
    }

    @Test
    void everyFleckFliesAUnitDirection() {
        FleckSling.fanDirections(new Vec3(0, -0.6, 0.8), RIGHT, 7, 60, true)
                .forEach(direction -> assertEquals(1, direction.length(), TOLERANCE));
    }

    @Test
    void aSingleFleckFliesTheLook() {
        assertEquals(List.of(LOOK), FleckSling.fanDirections(LOOK, RIGHT, 1, 60, true));
    }

    private static double degreesRightOfLook(Vec3 direction) {
        return Math.toDegrees(Math.atan2(direction.dot(RIGHT), direction.dot(LOOK)));
    }
}

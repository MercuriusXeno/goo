package com.mercuriusxeno.goo.ability.deep;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A diver enters stone from inside it, by sinking and by pushing a wall,
 * and walks open ground otherwise; inside it swims along the look, jump
 * lifting it, steering a share of the way each tick
 * (decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks).
 */
class DiveSwimTest {

    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final double CLOSE = 1e-9;

    @Nested
    class Entering {

        @Test
        void passesThroughFromInsideBySinkingOrByPushing() {
            assertTrue(DiveSwim.entersStone(true, false, false));
            assertTrue(DiveSwim.entersStone(false, true, false));
            assertTrue(DiveSwim.entersStone(false, false, true));
        }

        @Test
        void walksOpenGroundOtherwise() {
            assertFalse(DiveSwim.entersStone(false, false, false));
        }
    }

    @Nested
    class Swimming {

        @Test
        void forwardSteersAShareTowardTheLook() {
            Vec3 swum = DiveSwim.swum(Vec3.ZERO, 1, 0, 0, SOUTH);
            assertEquals(DiveSwim.SWIM_SPEED * DiveSwim.SWIM_STEER, swum.z, CLOSE);
            assertEquals(0, swum.x, CLOSE);
        }

        @Test
        void strafeLeftOfASouthLookIsEast() {
            Vec3 swum = DiveSwim.swum(Vec3.ZERO, 0, 1, 0, SOUTH);
            assertEquals(DiveSwim.SWIM_SPEED * DiveSwim.SWIM_STEER, swum.x, CLOSE);
        }

        @Test
        void jumpLiftsAndNoInputSlowsToRest() {
            assertTrue(DiveSwim.swum(Vec3.ZERO, 0, 0, 1, SOUTH).y > 0);
            Vec3 drifting = new Vec3(0, -0.4, 0);
            assertEquals(drifting.y * (1 - DiveSwim.SWIM_STEER), DiveSwim.swum(drifting, 0, 0, 0, SOUTH).y, CLOSE);
        }

        @Test
        void fullDiagonalInputKeepsTheSwimSpeed() {
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < 200; tick++) {
                velocity = DiveSwim.swum(velocity, 1, 1, 1, SOUTH);
            }
            assertEquals(DiveSwim.SWIM_SPEED, velocity.length(), 1e-6);
        }
    }
}

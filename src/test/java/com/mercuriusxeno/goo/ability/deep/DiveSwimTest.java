package com.mercuriusxeno.goo.ability.deep;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A diver presses into stone along its heading, up with jump and down with
 * sneak, and nowhere with no key held; it swims along the look, jump
 * lifting it, steering a share of the way each tick
 * (decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks).
 */
class DiveSwimTest {

    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final double CLOSE = 1e-9;

    @Nested
    class Pressing {

        private static final Input NONE = Input.EMPTY;

        @Test
        void forwardPressesAlongTheHeadingWhateverThePitch() {
            Input forward = new Input(true, false, false, false, false, false, false);
            Vec3 lookingDownSouth = new Vec3(0, -0.8, 0.6);
            assertEquals(List.of(SOUTH), DiveSwim.pressedDirections(forward, lookingDownSouth));
        }

        @Test
        void jumpPressesUpAndSneakPressesDown() {
            Input both = new Input(false, false, false, false, true, true, false);
            assertEquals(List.of(new Vec3(0, 1, 0), new Vec3(0, -1, 0)), DiveSwim.pressedDirections(both, SOUTH));
        }

        @Test
        void noKeyPressesNowhere() {
            assertTrue(DiveSwim.pressedDirections(NONE, SOUTH).isEmpty());
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

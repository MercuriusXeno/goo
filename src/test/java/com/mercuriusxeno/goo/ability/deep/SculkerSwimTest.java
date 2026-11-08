package com.mercuriusxeno.goo.ability.deep;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sneak and jump together enter the dive, jumping clear of a top face leaves
 * it and the brew's end ends it; sneak sinks over jump; the world's floor and
 * ceiling stop a body leaving and never one coming back; and the swim drives
 * along the look, steering a share of the way each tick
 * (decisions dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks and
 * sculker-swims-solid-blocks-and-surfaces-at-will).
 */
class SculkerSwimTest {

    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final double CLOSE = 1e-9;

    @Nested
    class Diving {

        private static final Input NONE = Input.EMPTY;
        private static final Input JUMP = new Input(false, false, false, false, true, false, false);
        private static final Input SNEAK_AND_JUMP = new Input(false, false, false, false, true, true, false);

        @Test
        void sneakAndJumpTogetherEnterTheDive() {
            assertTrue(SculkerSwim.nextDiving(false, true, SNEAK_AND_JUMP, false, true));
            assertFalse(SculkerSwim.nextDiving(false, true, JUMP, false, true));
        }

        @Test
        void jumpingClearOfATopFaceLeavesTheDive() {
            assertFalse(SculkerSwim.nextDiving(true, true, JUMP, false, true));
        }

        @Test
        void aDiverInsideStoneOrOverOpenAirStaysInTheDive() {
            assertTrue(SculkerSwim.nextDiving(true, true, JUMP, true, true));
            assertTrue(SculkerSwim.nextDiving(true, true, JUMP, false, false));
            assertTrue(SculkerSwim.nextDiving(true, true, NONE, false, true));
        }

        @Test
        void theDiveEndsWithTheBrew() {
            assertFalse(SculkerSwim.nextDiving(true, false, NONE, true, false));
        }

        @Test
        void sneakSinksOverJumpAndJumpLifts() {
            assertEquals(-1, SculkerSwim.riseOf(SNEAK_AND_JUMP));
            assertEquals(1, SculkerSwim.riseOf(JUMP));
            assertEquals(0, SculkerSwim.riseOf(NONE));
        }
    }

    @Nested
    class World {

        private static final double FLOOR = -64;
        private static final double CEILING = 320;

        @Test
        void aFallStopsAtTheFloor() {
            assertEquals(-0.5, SculkerSwim.heldInTheWorld(-1, FLOOR + 0.5, FLOOR + 2.3, FLOOR, CEILING), CLOSE);
        }

        @Test
        void aBodyBelowTheFloorRisesBackInAndFallsNoFurther() {
            assertEquals(0.3, SculkerSwim.heldInTheWorld(0.3, FLOOR - 2, FLOOR - 0.2, FLOOR, CEILING), CLOSE);
            assertEquals(0, SculkerSwim.heldInTheWorld(-0.3, FLOOR - 2, FLOOR - 0.2, FLOOR, CEILING), CLOSE);
        }

        @Test
        void aRiseStopsAtTheCeiling() {
            assertEquals(0.2, SculkerSwim.heldInTheWorld(1, CEILING - 2, CEILING - 0.2, FLOOR, CEILING), CLOSE);
        }
    }

    @Nested
    class Swimming {

        @Test
        void forwardSteersAShareTowardTheLook() {
            Vec3 swum = SculkerSwim.swum(Vec3.ZERO, 1, 0, 0, SOUTH);
            assertEquals(SculkerSwim.SWIM_SPEED * SculkerSwim.SWIM_STEER, swum.z, CLOSE);
            assertEquals(0, swum.x, CLOSE);
        }

        @Test
        void strafeLeftOfASouthLookIsEast() {
            Vec3 swum = SculkerSwim.swum(Vec3.ZERO, 0, 1, 0, SOUTH);
            assertEquals(SculkerSwim.SWIM_SPEED * SculkerSwim.SWIM_STEER, swum.x, CLOSE);
        }

        @Test
        void jumpLiftsAndNoInputSlowsToRest() {
            assertTrue(SculkerSwim.swum(Vec3.ZERO, 0, 0, 1, SOUTH).y > 0);
            Vec3 drifting = new Vec3(0, -0.4, 0);
            assertEquals(drifting.y * (1 - SculkerSwim.SWIM_STEER), SculkerSwim.swum(drifting, 0, 0, 0, SOUTH).y, CLOSE);
        }

        @Test
        void fullDiagonalInputKeepsTheSwimSpeed() {
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < 200; tick++) {
                velocity = SculkerSwim.swum(velocity, 1, 1, 1, SOUTH);
            }
            assertEquals(SculkerSwim.SWIM_SPEED, velocity.length(), 1e-6);
        }
    }
}

package com.mercuriusxeno.goo.ability.typhoon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Float's flight: in midair, jump climbs, sneak descends, forward flies along
 * the look vector and backward back along it, left and right strafe; with no
 * input the fall is slowed to the cap; the ground leaves vanilla's motion; an
 * elytra glide is drawn faster only while Float stands (decision
 * tailwind-and-airborn-merge-into-float).
 */
class AirbornMotionTest {

    /** Airborn carries floats, so sums of its values agree to a float's precision. */
    private static final double EPSILON = 1e-6;
    /** typhoon_float.json's flight, standing until tick 100. */
    private static final Airborn FLOAT = new Airborn(0.6f, 0.4f, 0.2f, 0.4f, 0.5f, 1.2f, 0.05f, 100L);
    /** Yaw 0 faces south, toward +z. */
    private static final float FACING_SOUTH = 0f;
    /** Looking south and up. */
    private static final Vec3 LOOK_SOUTH_AND_UP = new Vec3(0, 0.6, 0.8);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 BACKWARD = new Vec3(0, 0, -1);
    private static final Vec3 STRAFE_LEFT = new Vec3(1, 0, 0);
    private static final Vec3 JUMP = new Vec3(0, 1, 0);
    private static final Vec3 SNEAK = new Vec3(0, -1, 0);

    private static Vec3 flown(Vec3 velocity, Vec3 input) {
        return AirbornMotion.flown(velocity, input, LOOK_SOUTH_AND_UP, FACING_SOUTH, false, FLOAT);
    }

    @Nested
    class Flight {

        private final double pull = FLOAT.airSpeed() * FLOAT.airSteer();

        @Test
        void forwardFliesAlongTheLookVector() {
            Vec3 moved = flown(Vec3.ZERO, FORWARD);

            assertEquals(0, moved.x, EPSILON);
            assertEquals(LOOK_SOUTH_AND_UP.y * pull, moved.y, EPSILON);
            assertEquals(LOOK_SOUTH_AND_UP.z * pull, moved.z, EPSILON);
        }

        @Test
        void backwardFliesBackAlongTheLookVector() {
            Vec3 moved = flown(Vec3.ZERO, BACKWARD);

            assertEquals(-LOOK_SOUTH_AND_UP.y * pull, moved.y, EPSILON);
            assertEquals(-LOOK_SOUTH_AND_UP.z * pull, moved.z, EPSILON);
        }

        @Test
        void strafingLeftWhileFacingSouthMovesEastAndLeavesTheHeight() {
            Vec3 moved = flown(new Vec3(0, -0.1, 0), STRAFE_LEFT);

            assertEquals(pull, moved.x, EPSILON);
            assertEquals(-0.1, moved.y, EPSILON);
            assertEquals(0, moved.z, EPSILON);
        }

        @Test
        void jumpClimbs() {
            assertEquals(FLOAT.climbSpeed() * FLOAT.airSteer(), flown(Vec3.ZERO, JUMP).y, EPSILON);
        }

        @Test
        void sneakDescends() {
            assertEquals(-FLOAT.climbSpeed() * FLOAT.airSteer(), flown(Vec3.ZERO, SNEAK).y, EPSILON);
        }

        @Test
        void theGroundLeavesVanillasMotion() {
            Vec3 standing = new Vec3(0.1, 0, -0.05);

            assertEquals(standing, AirbornMotion.flown(standing, FORWARD, LOOK_SOUTH_AND_UP, FACING_SOUTH, true,
                    FLOAT));
        }
    }

    @Nested
    class Falling {

        @Test
        void noInputSinksAtTheSlowedFall() {
            Vec3 moved = flown(new Vec3(0.1, -2, 0.1), Vec3.ZERO);

            assertEquals(new Vec3(0.1, -FLOAT.fallCap(), 0.1), moved);
        }

        @Test
        void aFallUnderTheCapAndARiseStay() {
            assertEquals(-0.1, AirbornMotion.cappedFall(-0.1, FLOAT.fallCap()), EPSILON);
            assertEquals(0.2, AirbornMotion.cappedFall(0.2, FLOAT.fallCap()), EPSILON);
        }
    }

    @Nested
    class Gliding {

        /** Looking south, level. */
        private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);

        @Test
        void aSlowGlideIsDrawnAlongTheLookTowardTheGlideSpeed() {
            Vec3 slow = new Vec3(0, 0, 0.5);

            Vec3 glided = AirbornMotion.glided(slow, LOOK_SOUTH, FLOAT);

            assertEquals(0.5 + (FLOAT.glideSpeed() - 0.5) * FLOAT.glideSteer(), glided.z, EPSILON);
        }

        @Test
        void aGlideAtTheGlideSpeedOrPastItStays() {
            Vec3 diving = new Vec3(0, -1.5, 0.8);

            assertEquals(diving, AirbornMotion.glided(diving, LOOK_SOUTH, FLOAT));
        }

        @Test
        void noFloatLeavesTheGlideAtVanillaSpeed() {
            Vec3 slow = new Vec3(0, 0, 0.5);

            assertEquals(slow, AirbornMotion.glided(slow, LOOK_SOUTH, Airborn.NONE));
        }
    }
}

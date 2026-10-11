package com.mercuriusxeno.goo.ability.typhoon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Airborn's motion: midair input turns the horizontal velocity toward its
 * direction, levitation included, the ground and no input leave it be, a fall
 * stops at the cap, steering never brakes a faster flight, Jet pushes harder
 * and an elytra glide is drawn faster only while Airborn stands (decision
 * airborn-steerable-levitation-and-soft-falls).
 */
class AirbornMotionTest {

    /** Airborn carries floats, so sums of its values agree to a float's precision. */
    private static final double EPSILON = 1e-6;
    /** typhoon_airborn.json's values, standing until tick 100. */
    private static final Airborn AIRBORN = new Airborn(0.35f, 0.15f, 0.4f, 0.5f, 1.5f, 1.2f, 0.05f, 100L);
    /** Yaw 0 faces south, toward +z. */
    private static final float FACING_SOUTH = 0f;
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);
    private static final Vec3 STRAFE_LEFT = new Vec3(1, 0, 0);
    /** Levitation lifting the player. */
    private static final double RISING = 0.2;

    @Nested
    class Steering {

        @Test
        void forwardInputTurnsAStillLevitatingPlayerSouthAndKeepsItRising() {
            Vec3 moved = AirbornMotion.moved(new Vec3(0, RISING, 0), FORWARD, FACING_SOUTH, false, AIRBORN);

            assertEquals(0, moved.x, EPSILON);
            assertEquals(RISING, moved.y, EPSILON);
            assertEquals(AIRBORN.airSpeed() * AIRBORN.airSteer(), moved.z, EPSILON);
        }

        @Test
        void strafingLeftWhileFacingSouthSteersEast() {
            Vec3 heading = AirbornMotion.heading(STRAFE_LEFT, FACING_SOUTH);

            assertEquals(1, heading.x, EPSILON);
            assertEquals(0, heading.z, EPSILON);
        }

        @Test
        void inputTurnsAPlayerMovingTheOtherWayPartOfTheWayRound() {
            Vec3 northbound = new Vec3(0, 0, -AIRBORN.airSpeed());

            Vec3 moved = AirbornMotion.moved(northbound, FORWARD, FACING_SOUTH, false, AIRBORN);

            double expected = -AIRBORN.airSpeed() + 2 * AIRBORN.airSpeed() * AIRBORN.airSteer();
            assertEquals(expected, moved.z, EPSILON);
        }

        @Test
        void steeringTurnsAFasterFlightWithoutSlowingIt() {
            Vec3 jetting = new Vec3(0, 0, 1.2);

            Vec3 moved = AirbornMotion.moved(jetting, FORWARD, FACING_SOUTH, false, AIRBORN);

            assertEquals(jetting.z, moved.z, EPSILON);
        }

        @Test
        void theGroundAndNoInputLeaveTheHorizontalVelocityBe() {
            Vec3 drifting = new Vec3(0.1, 0, -0.05);

            assertEquals(drifting, AirbornMotion.moved(drifting, FORWARD, FACING_SOUTH, true, AIRBORN));
            assertEquals(drifting, AirbornMotion.moved(drifting, Vec3.ZERO, FACING_SOUTH, false, AIRBORN));
        }
    }

    @Nested
    class Falling {

        @Test
        void aFallStopsAtTheCap() {
            Vec3 moved = AirbornMotion.moved(new Vec3(0, -2, 0), Vec3.ZERO, FACING_SOUTH, false, AIRBORN);

            assertEquals(-AIRBORN.fallCap(), moved.y, EPSILON);
        }

        @Test
        void aFallUnderTheCapAndARiseStay() {
            assertEquals(-0.1, AirbornMotion.cappedFall(-0.1, AIRBORN.fallCap()), EPSILON);
            assertEquals(RISING, AirbornMotion.cappedFall(RISING, AIRBORN.fallCap()), EPSILON);
        }
    }

    @Nested
    class Jet {

        private static final double JET_STRENGTH = 0.8;
        /** typhoon_jet.json's steer share. */
        private static final double JET_STEER = 0.2;
        /** Vanilla's midair horizontal drag each tick. */
        private static final double AIR_DRAG = 0.91;
        private static final int SETTLING_TICKS = 200;
        /** The steady speed lands within this share of the boost. */
        private static final double BOOST_TOLERANCE = 0.02;

        @Test
        void jetFliesFasterByTheBoostWhileSteeringForwardUnderAirborn() {
            double boosted = steadyJetSpeed(AirbornMotion.jetStrength(JET_STRENGTH, AIRBORN, 0L), AIRBORN);
            double unboosted = steadyJetSpeed(JET_STRENGTH, null);

            assertEquals(AIRBORN.jetBoost(), boosted / unboosted, BOOST_TOLERANCE);
        }

        /**
         * The speed a player holding Jet and forward settles at: each tick
         * Jet's push steers the velocity toward the look, Airborn's steering
         * runs where it stands, then the air drags it.
         */
        private static double steadyJetSpeed(double strength, Airborn airborn) {
            Vec3 velocity = Vec3.ZERO;
            for (int tick = 0; tick < SETTLING_TICKS; tick++) {
                velocity = velocity.lerp(FORWARD.scale(strength), JET_STEER);
                if (airborn != null) {
                    velocity = AirbornMotion.moved(velocity, FORWARD, FACING_SOUTH, false, airborn);
                }
                velocity = velocity.scale(AIR_DRAG);
            }
            return velocity.z;
        }

        @Test
        void jetPushesHarderWhileAirbornStands() {
            assertEquals(JET_STRENGTH * AIRBORN.jetBoost(), AirbornMotion.jetStrength(JET_STRENGTH, AIRBORN, 99L),
                    EPSILON);
        }

        @Test
        void jetPushesAtItsOwnStrengthOnceAirbornEnds() {
            assertEquals(JET_STRENGTH, AirbornMotion.jetStrength(JET_STRENGTH, AIRBORN, 100L), EPSILON);
            assertEquals(JET_STRENGTH, AirbornMotion.jetStrength(JET_STRENGTH, Airborn.NONE, 0L), EPSILON);
        }
    }

    @Nested
    class Gliding {

        /** Looking south, level. */
        private static final Vec3 LOOK_SOUTH = new Vec3(0, 0, 1);

        @Test
        void aSlowGlideIsDrawnAlongTheLookTowardTheGlideSpeed() {
            Vec3 slow = new Vec3(0, 0, 0.5);

            Vec3 glided = AirbornMotion.glided(slow, LOOK_SOUTH, AIRBORN);

            assertEquals(0.5 + (AIRBORN.glideSpeed() - 0.5) * AIRBORN.glideSteer(), glided.z, EPSILON);
        }

        @Test
        void aGlideAtTheGlideSpeedOrPastItStays() {
            Vec3 diving = new Vec3(0, -1.5, 0.8);

            assertEquals(diving, AirbornMotion.glided(diving, LOOK_SOUTH, AIRBORN));
        }

        @Test
        void noAirbornLeavesTheGlideAtVanillaSpeed() {
            Vec3 slow = new Vec3(0, 0, 0.5);

            assertEquals(slow, AirbornMotion.glided(slow, LOOK_SOUTH, Airborn.NONE));
        }
    }
}

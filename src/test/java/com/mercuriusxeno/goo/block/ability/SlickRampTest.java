package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slick's ramp grows from kinetic_slick.json's starting ramp to its strongest
 * over the JSON's build ticks, scales the horizontal velocity of a thing on it
 * by that ramp, leaves its vertical velocity, and holds it to the JSON's top
 * speed at every point of the build.
 * slick-ramp-grows-while-a-thing-keeps-moving
 */
class SlickRampTest {

    private static final double EXACT = 1e-9;
    private static final Vec3 SLIDING = new Vec3(0.03, -0.2, -0.01);

    static SlickStep shippedSlick() {
        return AbilityJson.decode("kinetic_slick").behaviors().stream().filter(SlickStep.class::isInstance)
                .map(SlickStep.class::cast).findFirst().orElseThrow();
    }

    private static double rampApplied(int triedBefore, SlickStep slick) {
        Vec3 ramped = SlickRamp.ramped(SLIDING, SlickRamp.ramp(triedBefore, slick), slick.topSpeed());
        return ramped.horizontalDistance() / SLIDING.horizontalDistance();
    }

    @Nested
    class TheRampGrows {

        @Test
        void theFirstTickOfTryingAppliesTheStartingRamp() {
            SlickStep slick = shippedSlick();

            assertEquals(slick.startRamp(), rampApplied(0, slick), EXACT);
        }

        @Test
        void aLaterTickOfTryingAppliesAGreaterRamp() {
            SlickStep slick = shippedSlick();

            assertTrue(rampApplied(slick.buildTicks() / 2, slick) > rampApplied(0, slick),
                    "halfway through the build should ramp harder than its first tick");
        }

        @Test
        void onceTheBuildHasPassedEveryTickAppliesTheStrongestRamp() {
            SlickStep slick = shippedSlick();

            assertEquals(slick.strongestRamp(), rampApplied(slick.buildTicks(), slick), EXACT);
            assertEquals(slick.strongestRamp(), rampApplied(slick.buildTicks() * 3, slick), EXACT);
        }

        @Test
        void leavesTheVerticalVelocity() {
            SlickStep slick = shippedSlick();

            Vec3 ramped = SlickRamp.ramped(SLIDING, SlickRamp.ramp(0, slick), slick.topSpeed());

            assertEquals(SLIDING.y, ramped.y, EXACT);
        }
    }

    @Nested
    class ASlowEntry {

        private static final boolean STEPPED_ON = false;
        private static final boolean TRYING = true;

        @Test
        void isRaisedToTheStartSpeedAlongItsHeading() {
            SlickStep slick = shippedSlick();
            Vec3 slow = new Vec3(slick.startSpeed() * 0.03, -0.08, slick.startSpeed() * 0.04);

            Vec3 out = SlickRamp.slid(slow, STEPPED_ON, TRYING, 0, slick);

            assertEquals(slick.startSpeed(), out.horizontalDistance(), EXACT);
            assertEquals(3.0 / 4.0, out.x / out.z, EXACT);
            assertEquals(slow.y, out.y, EXACT);
        }

        @Test
        void landsAtTheStartSpeedOrAboveOnEveryHeading() {
            SlickStep slick = shippedSlick();
            for (int tenths = 0; tenths < 3600; tenths++) {
                double heading = Math.toRadians(tenths / 10.0);
                Vec3 slow = new Vec3(Math.cos(heading) * 0.0655, -0.08, Math.sin(heading) * 0.0655);

                double out = SlickRamp.slid(slow, STEPPED_ON, TRYING, 0, slick).horizontalDistance();

                assertTrue(out >= slick.startSpeed(), "heading " + tenths / 10.0 + " raised to " + out);
                assertEquals(slick.startSpeed(), out, EXACT);
            }
        }

        @Test
        void alreadyFasterGetsTheFirstTicksRampAlone() {
            SlickStep slick = shippedSlick();
            Vec3 fast = new Vec3(slick.startSpeed() * 0.9, 0, slick.startSpeed() * 1.2);

            Vec3 out = SlickRamp.slid(fast, STEPPED_ON, TRYING, 0, slick);

            assertEquals(SlickRamp.ramped(fast, SlickRamp.ramp(0, slick), slick.topSpeed()), out);
        }

        @Test
        void notTryingKeepsTheVelocityItArrivedWith() {
            SlickStep slick = shippedSlick();
            Vec3 slow = new Vec3(slick.startSpeed() * 0.03, -0.08, slick.startSpeed() * 0.04);

            Vec3 out = SlickRamp.slid(slow, STEPPED_ON, !TRYING, 0, slick);

            assertEquals(slow, out);
        }

        @Test
        void standingStillHasNoHeadingToRaiseAlong() {
            SlickStep slick = shippedSlick();
            Vec3 still = new Vec3(0, -0.08, 0);

            Vec3 out = SlickRamp.slid(still, STEPPED_ON, TRYING, 0, slick);

            assertEquals(still, out);
        }
    }

    @Nested
    class TheTopSpeedCaps {

        @Test
        void atTheStartOfTheBuild() {
            assertHeldToTopSpeed(0);
        }

        @Test
        void atTheStrongestRamp() {
            assertHeldToTopSpeed(shippedSlick().buildTicks());
        }

        private void assertHeldToTopSpeed(int triedBefore) {
            SlickStep slick = shippedSlick();
            Vec3 racing = new Vec3(slick.topSpeed() * 0.9, 0.1, slick.topSpeed() * 1.2);

            Vec3 ramped = SlickRamp.ramped(racing, SlickRamp.ramp(triedBefore, slick), slick.topSpeed());

            assertEquals(slick.topSpeed(), ramped.horizontalDistance(), EXACT);
            assertEquals(3.0 / 4.0, ramped.x / ramped.z, EXACT);
            assertEquals(racing.y, ramped.y, EXACT);
        }
    }
}

package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.client.zone.LunarSky;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lunar trip's curves: over a moon fixation the sky dims and the land darkens from none to full, and the moon
 * swells from vanilla's size to cover the whole view; white floods in to stand full at the carry, holds while the
 * arrival mark is awaited, and clears after it lands.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
class LunarTripTest {

    private static final int SAMPLES = 200;
    private static final double EXACT = 1e-6;

    /**
     * Whether a curve never falls across the fixation and ends above where it starts.
     *
     * @param curve the curve against progress
     * @return true where it rises
     */
    private static boolean rises(DoubleUnaryOperator curve) {
        double last = curve.applyAsDouble(0.0);
        for (int sample = 1; sample <= SAMPLES; sample++) {
            double now = curve.applyAsDouble((double) sample / SAMPLES);
            if (now < last) {
                return false;
            }
            last = now;
        }
        return last > curve.applyAsDouble(0.0);
    }

    @Nested
    class SkyDim {
        @Test
        void skyStandsUndimmedAtTheStartAndFullyDimAtTheEnd() {
            assertEquals(0.0, LunarTrip.skyDim(0F), EXACT);
            assertEquals(1.0, LunarTrip.skyDim(1F), EXACT);
        }

        @Test
        void skyDimRisesAcrossTheFixation() {
            assertTrue(rises(progress -> LunarTrip.skyDim((float) progress)));
        }
    }

    @Nested
    class LandDark {
        @Test
        void landStandsLitAtTheStartAndFullyDarkAtTheEnd() {
            assertEquals(0.0, LunarTrip.landDark(0F), EXACT);
            assertEquals(1.0, LunarTrip.landDark(1F), EXACT);
        }

        @Test
        void landDarkRisesAcrossTheFixation() {
            assertTrue(rises(progress -> LunarTrip.landDark((float) progress)));
        }
    }

    @Nested
    class White {
        private static final float BEFORE_THE_FLOOD = 0.5F;
        private static final float PAST_THE_FADE = 1000F;

        @Test
        void viewStandsClearBeforeTheFlood() {
            assertEquals(0.0, LunarTrip.whiteBeforeCarry(BEFORE_THE_FLOOD), EXACT);
        }

        @Test
        void whiteStandsFullAtTheCarry() {
            assertEquals(1.0, LunarTrip.whiteBeforeCarry(1F), EXACT);
        }

        @Test
        void whiteHoldsFullWhileTheArrivalMarkIsAwaited() {
            assertEquals(1.0, LunarTrip.whiteAfterCarry(false, PAST_THE_FADE), EXACT);
        }

        @Test
        void whiteHoldsFullAsTheArrivalMarkLands() {
            assertEquals(1.0, LunarTrip.whiteAfterCarry(true, 0F), EXACT);
        }

        @Test
        void whiteClearsOnceTheFadeAfterArrivalRuns() {
            assertEquals(0.0, LunarTrip.whiteAfterCarry(true, PAST_THE_FADE), EXACT);
        }

        @Test
        void whiteFloodRisesAcrossTheFixation() {
            assertTrue(rises(progress -> LunarTrip.whiteBeforeCarry((float) progress)));
        }
    }

    @Nested
    class RunBack {
        private static final int FIXATION_TICKS = 100;
        private static final float REACHED = 0.6F;
        private static final float RISE_TICKS = REACHED * FIXATION_TICKS;

        @Test
        void runBackStartsFromTheProgressReachedAtRelease() {
            assertEquals(REACHED, LunarTrip.progressRunningBack(REACHED, FIXATION_TICKS, 0F), EXACT);
        }

        @Test
        void runBackStandsPartwayBetweenTheReachAndNoneMidway() {
            float midway = LunarTrip.runBackTicks(REACHED, FIXATION_TICKS) / 2F;
            float progress = LunarTrip.progressRunningBack(REACHED, FIXATION_TICKS, midway);
            assertTrue(progress > 0F && progress < REACHED, "midway through the run back the trip stands at "
                    + progress + ", not between none and " + REACHED);
        }

        @Test
        void runBackEndsAtNone() {
            float end = LunarTrip.runBackTicks(REACHED, FIXATION_TICKS);
            assertEquals(0.0, LunarTrip.progressRunningBack(REACHED, FIXATION_TICKS, end), EXACT);
        }

        @Test
        void runBackTakesSeveralTicksYetFewerThanTheRise() {
            float runBack = LunarTrip.runBackTicks(REACHED, FIXATION_TICKS);
            assertTrue(runBack > 1F && runBack < RISE_TICKS,
                    "the run back takes " + runBack + " ticks against a rise of " + RISE_TICKS);
        }
    }

    /**
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     */
    @Nested
    class PlanetSize {
        /** A fixation's direction the planet stood along as the trip home began: well up in the west. */
        private static final Vec3 FIXATION_DIRECTION = LunarSight.moonDirection(40F);
        private static final double QUAD_TOLERANCE = 1e-5;

        @Test
        void bluePlanetStandsAtItsSizeInLunarsSkyAtTheStart() {
            assertEquals(LunarSky.PLANET_HALF_SIZE / LunarTrip.SKY_DISTANCE, LunarTrip.planetHalfTangent(0F), EXACT);
        }

        @Test
        void bluePlanetCoversTheWidestViewAtTheEnd() {
            assertTrue(LunarTrip.planetHalfTangent(1F) >= LunarTrip.WIDEST_VIEW_HALF_TANGENT,
                    "the swollen planet's half width " + LunarTrip.planetHalfTangent(1F)
                            + " should reach the widest view's half diagonal " + LunarTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        @Test
        void bluePlanetSwellsAcrossTheFixation() {
            assertTrue(rises(progress -> LunarTrip.planetHalfTangent((float) progress)));
        }

        @Test
        void theTripHomeDrawsThePlanetDiscAtTheFixationsDirectionAtItsSwollenSize() {
            for (float progress : new float[] {0F, 0.5F, 0.9F}) {
                double halfTangent = LunarTrip.planetHalfTangent(progress);
                List<Vector3f> corners = LunarTripBodies.planetCorners(FIXATION_DIRECTION, halfTangent);
                Vector3f center = new Vector3f();
                corners.forEach(center::add);
                center.div(corners.size());
                assertEquals(FIXATION_DIRECTION.x(), center.x(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(FIXATION_DIRECTION.y(), center.y(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(FIXATION_DIRECTION.z(), center.z(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(halfTangent * Math.sqrt(2), corners.get(0).distance(center), QUAD_TOLERANCE,
                        "at " + progress);
            }
        }
    }

    @Nested
    class MoonSize {
        @Test
        void moonStandsAtVanillasSizeAtTheStart() {
            assertEquals(LunarTrip.RESTING_MOON_HALF_TANGENT, LunarTrip.moonHalfTangent(0F), EXACT);
        }

        @Test
        void moonCoversTheWidestViewAtTheEnd() {
            assertTrue(LunarTrip.moonHalfTangent(1F) >= LunarTrip.WIDEST_VIEW_HALF_TANGENT,
                    "the swollen moon's half width " + LunarTrip.moonHalfTangent(1F)
                            + " should reach the widest view's half diagonal " + LunarTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        @Test
        void moonSwellsAcrossTheFixation() {
            assertTrue(rises(progress -> LunarTrip.moonHalfTangent((float) progress)));
        }
    }
}

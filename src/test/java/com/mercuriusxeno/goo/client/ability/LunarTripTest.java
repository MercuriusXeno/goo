package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.client.BlendComposite;
import com.mercuriusxeno.goo.client.zone.LunarSky;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        private static final int PROGRESS_STEPS = 20;
        private static final double RATIO_TOLERANCE = 1e-9;
        private static final int FIXATION_TICKS = 100;
        /** A moment of the hold before its end, by which the planet covers the widest view. */
        private static final float BEFORE_THE_END = 0.9F;
        /** Vanilla's near plane's distance from the camera. */
        private static final float VANILLA_NEAR_PLANE = 0.05F;

        /**
         * The planet the trip swells stands as solid as the one the sky drew: its unlit face, opaque black, hides what
         * lies behind it.
         * decision sun-follows-the-phase-and-lights-the-far-side-too
         */
        @Test
        void bluePlanetsDarkFaceHidesWhatLiesBehindIt() {
            float[] shown = BlendComposite.over(LunarTripBodies.PLANET_PIPELINE, new float[] {0F, 0F, 0F, 1F},
                    new float[] {1F, 0.9F, 0.5F});
            assertArrayEquals(new float[] {0F, 0F, 0F}, shown, 1e-6F, "behind the swelling dark planet shows "
                    + shown[0] + ", " + shown[1] + ", " + shown[2]);
        }

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

        /**
         * decision home-swell-feels-as-far-as-the-moon-pull
         */
        @Test
        void bluePlanetGrowsByTheMoonsFactorAtEveryMoment() {
            for (int step = 0; step <= PROGRESS_STEPS; step++) {
                float progress = (float) step / PROGRESS_STEPS;
                double planetGrowth = LunarTrip.planetHalfTangent(progress) / LunarTrip.planetHalfTangent(0F);
                double moonGrowth = LunarTrip.moonHalfTangent(progress) / LunarTrip.moonHalfTangent(0F);
                assertEquals(moonGrowth, planetGrowth, RATIO_TOLERANCE, "at " + progress + " the planet grew "
                        + planetGrowth + " times, the moon " + moonGrowth);
            }
        }

        /**
         * decision home-swell-feels-as-far-as-the-moon-pull
         */
        @Test
        void bluePlanetGrowsFromTheFirstTickOnEveryTick() {
            for (int tick = 1; tick <= FIXATION_TICKS; tick++) {
                double before = LunarTrip.planetHalfTangent((float) (tick - 1) / FIXATION_TICKS);
                double now = LunarTrip.planetHalfTangent((float) tick / FIXATION_TICKS);
                assertTrue(now > before, "at tick " + tick + " the planet's half width " + now
                        + " should stand above tick " + (tick - 1) + "'s " + before);
            }
        }

        /**
         * decision home-swell-feels-as-far-as-the-moon-pull
         */
        @Test
        void bluePlanetCoversTheWidestViewBeforeTheHoldEnds() {
            assertTrue(LunarTrip.planetHalfTangent(BEFORE_THE_END) >= LunarTrip.WIDEST_VIEW_HALF_TANGENT,
                    "at " + BEFORE_THE_END + " the planet's half width " + LunarTrip.planetHalfTangent(BEFORE_THE_END)
                            + " should reach the widest view's half diagonal " + LunarTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        /**
         * The fully swollen planet's quad stands centered on the fixation's direction and in front of vanilla's near
         * plane: each corner's depth along the direction the view holds, not its distance from the camera, clears it.
         * decision home-swell-feels-as-far-as-the-moon-pull
         */
        @Test
        void theFullySwollenPlanetDrawsCenteredInFrontOfTheNearPlane() {
            List<Vector3f> corners = LunarTripBodies.planetCorners(FIXATION_DIRECTION, LunarTrip.planetHalfTangent(1F));
            Vector3f center = new Vector3f();
            corners.forEach(center::add);
            center.div(corners.size());
            Vector3f along = new Vector3f(center).normalize();
            assertEquals(FIXATION_DIRECTION.x(), along.x(), QUAD_TOLERANCE, "center " + center);
            assertEquals(FIXATION_DIRECTION.y(), along.y(), QUAD_TOLERANCE, "center " + center);
            assertEquals(FIXATION_DIRECTION.z(), along.z(), QUAD_TOLERANCE, "center " + center);
            for (Vector3f corner : corners) {
                double depth = corner.x() * FIXATION_DIRECTION.x() + corner.y() * FIXATION_DIRECTION.y()
                        + corner.z() * FIXATION_DIRECTION.z();
                assertTrue(depth > VANILLA_NEAR_PLANE, "corner " + corner + " stands " + depth
                        + " deep along the fixation's direction, inside vanilla's near plane " + VANILLA_NEAR_PLANE);
            }
        }

        /**
         * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
         */
        @Test
        void theTripHomesPlanetAtRestStandsOnLunarsSkyPlanetQuad() {
            for (double cameraX : new double[] {0, 1_500_000, -3_000_000}) {
                List<Vector3f> trip = LunarTripBodies.planetCorners(LunarSight.planetDirection(cameraX),
                        LunarTrip.planetHalfTangent(0F));
                List<Vector3f> sky = LunarSky.planetCorners(cameraX);
                for (int corner = 0; corner < trip.size(); corner++) {
                    String at = "corner " + corner + " at " + cameraX + ": trip " + trip.get(corner) + " against sky "
                            + sky.get(corner);
                    assertEquals(sky.get(corner).x(), trip.get(corner).x(), 1e-3, at);
                    assertEquals(sky.get(corner).y(), trip.get(corner).y(), 1e-3, at);
                    assertEquals(sky.get(corner).z(), trip.get(corner).z(), 1e-3, at);
                }
            }
        }

        @Test
        void theTripHomeDrawsThePlanetDiscAtTheFixationsDirectionAtItsSwollenSize() {
            for (float progress : new float[] {0F, 0.5F, 0.9F}) {
                double halfTangent = LunarTrip.planetHalfTangent(progress);
                List<Vector3f> corners = LunarTripBodies.planetCorners(FIXATION_DIRECTION, halfTangent);
                Vector3f center = new Vector3f();
                corners.forEach(center::add);
                center.div(corners.size());
                float distance = center.length();
                Vector3f along = new Vector3f(center).div(distance);
                assertEquals(FIXATION_DIRECTION.x(), along.x(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(FIXATION_DIRECTION.y(), along.y(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(FIXATION_DIRECTION.z(), along.z(), QUAD_TOLERANCE, "at " + progress);
                assertEquals(halfTangent * Math.sqrt(2), corners.get(0).distance(center) / distance, QUAD_TOLERANCE,
                        "at " + progress);
            }
        }
    }

    /**
     * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
     */
    @Nested
    class MoonAsTheHoldTakes {
        /** Vanilla's sky: SkyRenderer turns its moon a quarter turn about y, then the moon angle about x. */
        private static final float VANILLA_SKY_TURN = (float) Math.toRadians(-90.0);
        private static final float VANILLA_MOON_DISTANCE = 100F;
        private static final float VANILLA_MOON_HALF_WIDTH = 20F;
        /** How far the moon angle runs in a tick: a full turn over a day of 24000 ticks. */
        private static final float DEGREES_A_TICK = 360F / 24000F;
        private static final float[] TICK_ANGLES = {0F, 30F, 85F, 300F, 337.5F};
        private static final float[] PARTIAL_TICKS = {0F, 0.25F, 0.5F, 0.99F};
        private static final float CORNER_TOLERANCE = 1e-3F;

        private static List<Vector3f> vanillaMoonCorners(float moonAngle) {
            Matrix4f pose = new Matrix4f().rotateY(VANILLA_SKY_TURN).rotateX(moonAngle)
                    .translate(0F, VANILLA_MOON_DISTANCE, 0F)
                    .scale(VANILLA_MOON_HALF_WIDTH, 1F, VANILLA_MOON_HALF_WIDTH);
            return LunarSky.FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
        }

        @Test
        void theTripsMoonAtRestStandsOnVanillasMoonQuadAtEveryFrameAngle() {
            for (float tickAngle : TICK_ANGLES) {
                for (float partial : PARTIAL_TICKS) {
                    float frameAngle = (float) Math.toRadians(tickAngle + partial * DEGREES_A_TICK);
                    List<Vector3f> trip = LunarTripBodies.moonCorners(frameAngle, LunarTrip.moonHalfTangent(0F));
                    List<Vector3f> vanilla = vanillaMoonCorners(frameAngle);
                    for (int corner = 0; corner < trip.size(); corner++) {
                        String at = "corner " + corner + " at " + tickAngle + " degrees, partial " + partial
                                + ": trip " + trip.get(corner) + " against vanilla " + vanilla.get(corner);
                        assertEquals(vanilla.get(corner).x(), trip.get(corner).x(), CORNER_TOLERANCE, at);
                        assertEquals(vanilla.get(corner).y(), trip.get(corner).y(), CORNER_TOLERANCE, at);
                        assertEquals(vanilla.get(corner).z(), trip.get(corner).z(), CORNER_TOLERANCE, at);
                    }
                }
            }
        }
    }

    @Nested
    class MoonSize {
        private static final double PINNED = 1e-12;

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

        /**
         * The moon pull stands as it stood before the trip home's swell took the moon's factor: its half width's
         * tangent at five moments of the hold, computed on the branch's base.
         * decision home-swell-feels-as-far-as-the-moon-pull
         */
        @Test
        void moonPullStandsAsItStoodBeforeTheHomeSwellChanged() {
            float[] moments = {0F, 0.25F, 0.5F, 0.75F, 1F};
            double[] before = {0.05000000074505806, 0.07846944971689726, 0.16630996616728563, 0.642850649815684,
                4.531854658724893};
            for (int moment = 0; moment < moments.length; moment++) {
                assertEquals(before[moment], LunarTrip.moonHalfTangent(moments[moment]), PINNED,
                        "at " + moments[moment]);
            }
        }
    }

    /**
     * decision lunar-moon-moves-from-the-first-tick-on-the-slow-ease
     */
    @Nested
    class SwellFromTheFirstTick {
        private static final int FIXATION_TICKS = 100;
        /**
         * How far a ratio may read below the one before it and still stand level: the noise a float progress leaves,
         * near 1e-8 over the steady creep, against the rush's steps of near 1e-4.
         */
        private static final double LEVEL = 1e-7;

        private static double moonAtTick(int tick) {
            return LunarTrip.moonHalfTangent((float) tick / FIXATION_TICKS);
        }

        @Test
        void moonGrowsFromTheFirstTickOnEveryTickToCoverTheWidestView() {
            assertTrue(moonAtTick(1) > LunarTrip.RESTING_MOON_HALF_TANGENT, "at tick 1 the moon's half width "
                    + moonAtTick(1) + " should stand above its resting " + LunarTrip.RESTING_MOON_HALF_TANGENT);
            for (int tick = 2; tick <= FIXATION_TICKS; tick++) {
                assertTrue(moonAtTick(tick) > moonAtTick(tick - 1), "at tick " + tick + " the moon's half width "
                        + moonAtTick(tick) + " should stand above tick " + (tick - 1) + "'s " + moonAtTick(tick - 1));
            }
            assertTrue(moonAtTick(FIXATION_TICKS) >= LunarTrip.WIDEST_VIEW_HALF_TANGENT);
        }

        @Test
        void moonsGrowthFromTickToTickStartsAboveNoneAndNeverFalls() {
            double before = moonAtTick(1) / moonAtTick(0);
            assertTrue(before > 1.0, "the first tick's growth ratio " + before + " should stand above 1");
            for (int tick = 2; tick <= FIXATION_TICKS; tick++) {
                double ratio = moonAtTick(tick) / moonAtTick(tick - 1);
                assertTrue(ratio >= before - LEVEL, "the growth ratio into tick " + tick + ", " + ratio
                        + ", should stand at or above the one before it, " + before);
                before = ratio;
            }
        }
    }
}

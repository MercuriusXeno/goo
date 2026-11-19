package com.mercuriusxeno.goo.ability.astral;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.util.EasingType;
import net.minecraft.world.level.MoonPhase;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the moon lands a caster on lunar: the phase picks one eighth of the light side and the moon's time in the sky
 * sweeps x evenly through it, and z is the average of the caster's overworld x and z wrapped into the moon's range.
 * The moon's time in the sky is checked against vanilla's own day timeline, read off the classpath.
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 * decision traversal-to-the-moon-always-lands-on-the-light-side
 */
class LunarLandingTest {

    private static final double TOLERANCE = 1e-6;
    private static final double LIGHT_SIDE_EDGE = Lunar.LIGHT_SIDE_HALF_WIDTH;
    private static final double MOON_HALF_DEPTH = 7_500_000;
    private static final String DAY_TIMELINE = "data/minecraft/timeline/day.json";
    private static final String MOON_ANGLE_TRACK = "minecraft:visual/moon_angle";
    /** The day tick vanilla's moon angle track keys its turn from. */
    private static final int MOON_ANGLE_KEY_TICK = 6_000;
    /** The moon angle the track starts its turn at, in degrees, and the whole turn it makes over a day. */
    private static final float MOON_ANGLE_START = 180F;
    private static final float FULL_TURN = 360F;

    @Nested
    class TimeAndPhaseSetX {

        @Test
        void sameTickOfSamePhaseLandsSameX() {
            for (int phase = 0; phase < MoonPhase.COUNT; phase++) {
                double first = LunarLanding.at(phase, 1234, 10, 20).x();
                double second = LunarLanding.at(phase, 1234, -500, 9000).x();
                assertEquals(first, second, TOLERANCE, "phase " + phase);
            }
        }

        @Test
        void eachTickLaterLandsOneConstantStepFartherAcrossAWholeNight() {
            double step = LunarLanding.at(0, 1, 0, 0).x() - LunarLanding.at(0, 0, 0, 0).x();
            assertTrue(step > 0, "a tick later should land farther along x, stepped " + step);
            for (int tick = 1; tick < LunarLanding.NIGHT_TICKS; tick++) {
                double stepped = LunarLanding.at(0, tick, 0, 0).x() - LunarLanding.at(0, tick - 1, 0, 0).x();
                assertEquals(step, stepped, TOLERANCE, "night tick " + tick);
            }
        }

        @Test
        void nightTickCountsFromMoonriseOnAnyDay() {
            long day = 7L * LunarLanding.DAY_TICKS;
            assertEquals(0, LunarLanding.nightTickAt(day + LunarLanding.MOONRISE_TICK));
            assertEquals(LunarLanding.NIGHT_TICKS - 1, LunarLanding.nightTickAt(day + LunarLanding.MOONSET_TICK));
            assertEquals(100, LunarLanding.nightTickAt(day + LunarLanding.MOONRISE_TICK + 100));
        }
    }

    @Nested
    class AlwaysOnTheLightSide {

        @Test
        void everyPhaseAndEveryNightTickLandsOnTheLightSide() {
            for (int phase = 0; phase < MoonPhase.COUNT; phase++) {
                for (int tick = 0; tick < LunarLanding.NIGHT_TICKS; tick++) {
                    double x = LunarLanding.at(phase, tick, 0, 0).x();
                    assertTrue(x >= -LIGHT_SIDE_EDGE && x < LIGHT_SIDE_EDGE,
                            "phase " + phase + " tick " + tick + " landed x " + x);
                }
            }
        }

        @Test
        void eightPhasesCoverTheLightSideWithNoGapAndNoOverlap() {
            double step = LunarLanding.at(0, 1, 0, 0).x() - LunarLanding.at(0, 0, 0, 0).x();
            assertEquals(-LIGHT_SIDE_EDGE, LunarLanding.at(0, 0, 0, 0).x(), TOLERANCE);
            for (int phase = 0; phase < MoonPhase.COUNT - 1; phase++) {
                double phaseEnd = LunarLanding.at(phase, LunarLanding.NIGHT_TICKS - 1, 0, 0).x() + step;
                assertEquals(phaseEnd, LunarLanding.at(phase + 1, 0, 0, 0).x(), TOLERANCE, "phase " + phase);
            }
            double lastEnd = LunarLanding.at(MoonPhase.COUNT - 1, LunarLanding.NIGHT_TICKS - 1, 0, 0).x() + step;
            assertEquals(LIGHT_SIDE_EDGE, lastEnd, TOLERANCE);
        }

        @Test
        void ticksPastTheNightLandInsideTheirPhase() {
            long day = 3L * LunarLanding.DAY_TICKS;
            assertEquals(0, LunarLanding.nightTickAt(day + LunarLanding.MOONRISE_TICK - 1));
            assertEquals(LunarLanding.NIGHT_TICKS - 1, LunarLanding.nightTickAt(day + LunarLanding.MOONSET_TICK + 1));
        }

        @Test
        void theNightIsEveryDayTickVanillasMoonStandsAboveTheHorizon() throws IOException {
            EasingType ease = moonAngleEase();
            List<String> wrong = new ArrayList<>();
            for (int tick = 0; tick < LunarLanding.DAY_TICKS; tick++) {
                float progress = Math.floorMod(tick - MOON_ANGLE_KEY_TICK, LunarLanding.DAY_TICKS)
                        / (float) LunarLanding.DAY_TICKS;
                float angle = MOON_ANGLE_START + FULL_TURN * ease.apply(progress);
                boolean up = LunarSight.moonDirection(angle).y() > 0;
                boolean night = tick >= LunarLanding.MOONRISE_TICK && tick <= LunarLanding.MOONSET_TICK;
                if (up != night) {
                    wrong.add(tick + (up ? " up" : " down"));
                }
            }
            assertTrue(wrong.isEmpty(), "the night should hold every tick the moon is up and no other: " + wrong);
        }
    }

    @Nested
    class OverworldSpotSetsZ {

        @Test
        void zIsTheAverageOfOverworldXAndZ() {
            assertEquals(2000, LunarLanding.at(0, 0, 1000, 3000).z(), TOLERANCE);
        }

        @Test
        void anAveragePastTheMoonsRangeWrapsIntoIt() {
            double z = LunarLanding.at(0, 0, 20_000_000, 20_000_000).z();
            assertEquals(5_000_000, z, TOLERANCE);
            assertTrue(z >= -MOON_HALF_DEPTH && z < MOON_HALF_DEPTH, "wrapped z " + z);
        }

        @Test
        void anAverageBelowTheMoonsRangeWrapsIntoIt() {
            assertEquals(5_000_000, LunarLanding.at(0, 0, -10_000_000, -10_000_000).z(), TOLERANCE);
        }
    }

    private static EasingType moonAngleEase() throws IOException {
        try (InputStream day = LunarLandingTest.class.getClassLoader().getResourceAsStream(DAY_TIMELINE)) {
            assertNotNull(day, DAY_TIMELINE + " should be on the classpath");
            JsonObject track = JsonParser.parseReader(new InputStreamReader(day, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("tracks").getAsJsonObject(MOON_ANGLE_TRACK);
            JsonArray controls = track.getAsJsonObject("ease").getAsJsonArray("cubic_bezier");
            return EasingType.cubicBezier(controls.get(0).getAsFloat(), controls.get(1).getAsFloat(),
                    controls.get(2).getAsFloat(), controls.get(3).getAsFloat());
        }
    }
}

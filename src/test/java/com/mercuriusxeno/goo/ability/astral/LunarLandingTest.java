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
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the moon lands a caster on lunar: the phase picks one eighth of the light side and the moon's time in the sky
 * sweeps x evenly through it, and z is a function of the caster's account UUID within the band around z 0.
 * The moon's time in the sky is checked against vanilla's own day timeline, read off the classpath.
 * decision time-and-phase-set-x-and-your-overworld-spot-sets-z
 * decision z-is-a-function-of-the-account-uuid
 * decision traversal-to-the-moon-always-lands-on-the-light-side
 */
class LunarLandingTest {

    private static final double TOLERANCE = 1e-6;
    private static final double LIGHT_SIDE_EDGE = Lunar.LIGHT_SIDE_HALF_WIDTH;
    private static final UUID CASTER = UUID.fromString("6f1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
    private static final UUID OTHER_CASTER = UUID.fromString("0a9b8c7d-6e5f-4d3c-9b2a-1f0e9d8c7b6a");
    /** The band every landing's z falls inside, plus and minus, in blocks: the operator's number. */
    private static final double Z_BAND = 50_000;
    private static final double Z_QUARTER = Z_BAND / 2;
    private static final long SEED = 0x5EED_1A2B_3C4DL;
    private static final int DRAWN_UUIDS = 10_000;
    private static final int SEQUENTIAL_UUIDS = 1_000;
    private static final double LEAST_MEAN_GAP = 1_000;
    /** One eighth of the light side, 937,500 blocks, over the night's 10,437 ticks. */
    private static final double ONE_TICKS_STEP = 89.8;
    private static final double STEP_TOLERANCE = 0.05;
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
        void sameTickOfSamePhaseLandsSameXForEveryCaster() {
            for (int phase = 0; phase < MoonPhase.COUNT; phase++) {
                double first = LunarLanding.at(phase, 1234, CASTER).x();
                double second = LunarLanding.at(phase, 1234, OTHER_CASTER).x();
                assertEquals(first, second, TOLERANCE, "phase " + phase);
            }
        }

        @Test
        void phaseAndTickLandXAlongTheLightSide() {
            assertEquals(-LIGHT_SIDE_EDGE, LunarLanding.at(0, 0, CASTER).x(), TOLERANCE);
            assertEquals(-937_500, LunarLanding.at(3, 0, CASTER).x(), TOLERANCE);
            double step = LunarLanding.at(3, 1, CASTER).x() - LunarLanding.at(3, 0, CASTER).x();
            assertEquals(ONE_TICKS_STEP, step, STEP_TOLERANCE);
            double lastX = LunarLanding.at(MoonPhase.COUNT - 1, LunarLanding.NIGHT_TICKS - 1, CASTER).x();
            assertTrue(lastX < LIGHT_SIDE_EDGE, "phase 7's last night tick landed x " + lastX);
        }

        @Test
        void eachTickLaterLandsOneConstantStepFartherAcrossAWholeNight() {
            double step = LunarLanding.at(0, 1, CASTER).x() - LunarLanding.at(0, 0, CASTER).x();
            assertTrue(step > 0, "a tick later should land farther along x, stepped " + step);
            for (int tick = 1; tick < LunarLanding.NIGHT_TICKS; tick++) {
                double stepped = LunarLanding.at(0, tick, CASTER).x() - LunarLanding.at(0, tick - 1, CASTER).x();
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
                    double x = LunarLanding.at(phase, tick, CASTER).x();
                    assertTrue(x >= -LIGHT_SIDE_EDGE && x < LIGHT_SIDE_EDGE,
                            "phase " + phase + " tick " + tick + " landed x " + x);
                }
            }
        }

        @Test
        void eightPhasesCoverTheLightSideWithNoGapAndNoOverlap() {
            double step = LunarLanding.at(0, 1, CASTER).x() - LunarLanding.at(0, 0, CASTER).x();
            assertEquals(-LIGHT_SIDE_EDGE, LunarLanding.at(0, 0, CASTER).x(), TOLERANCE);
            for (int phase = 0; phase < MoonPhase.COUNT - 1; phase++) {
                double phaseEnd = LunarLanding.at(phase, LunarLanding.NIGHT_TICKS - 1, CASTER).x() + step;
                assertEquals(phaseEnd, LunarLanding.at(phase + 1, 0, CASTER).x(), TOLERANCE, "phase " + phase);
            }
            double lastEnd = LunarLanding.at(MoonPhase.COUNT - 1, LunarLanding.NIGHT_TICKS - 1, CASTER).x() + step;
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
    class AccountUuidSetsZ {

        @Test
        void oneUuidLandsOneZAtEveryPhaseAndTickAndTwoUuidsLandTwo() {
            double z = LunarLanding.at(0, 0, CASTER).z();
            assertEquals(z, LunarLanding.at(5, 0, CASTER).z(), TOLERANCE);
            assertEquals(z, LunarLanding.at(0, 9_000, CASTER).z(), TOLERANCE);
            assertEquals(z, LunarLanding.at(5, 9_000, CASTER).z(), TOLERANCE);
            assertNotEquals(z, LunarLanding.at(0, 0, OTHER_CASTER).z(), "two uuids landed one z " + z);
        }

        @Test
        void randomUuidsSpreadAcrossTheBand() {
            Random seeded = new Random(SEED);
            List<UUID> uuids = new ArrayList<>();
            for (int drawn = 0; drawn < DRAWN_UUIDS; drawn++) {
                uuids.add(new UUID(seeded.nextLong(), seeded.nextLong()));
            }
            assertSpreadAcrossTheBand(uuids, "random");
        }

        @Test
        void sequentialUuidsSpreadAcrossTheBandAndApart() {
            List<UUID> lowHalfCounts = new ArrayList<>();
            List<UUID> highHalfCounts = new ArrayList<>();
            for (int count = 0; count < SEQUENTIAL_UUIDS; count++) {
                lowHalfCounts.add(new UUID(SEED, count));
                highHalfCounts.add(new UUID(count, SEED));
            }
            assertSpreadAcrossTheBand(lowHalfCounts, "sequential low half");
            assertSpreadAcrossTheBand(highHalfCounts, "sequential high half");
            assertNeighboursLandApart(lowHalfCounts, "sequential low half");
            assertNeighboursLandApart(highHalfCounts, "sequential high half");
        }

        private static void assertSpreadAcrossTheBand(List<UUID> uuids, String set) {
            boolean belowQuarter = false;
            boolean aboveQuarter = false;
            for (UUID uuid : uuids) {
                double z = LunarLanding.at(0, 0, uuid).z();
                assertTrue(z >= -Z_BAND && z <= Z_BAND, set + " uuid " + uuid + " landed z " + z);
                belowQuarter |= z < -Z_QUARTER;
                aboveQuarter |= z > Z_QUARTER;
            }
            assertTrue(belowQuarter && aboveQuarter, set + " uuids should land on both sides of z 0 past "
                    + Z_QUARTER + ", below " + belowQuarter + " above " + aboveQuarter);
        }

        private static void assertNeighboursLandApart(List<UUID> uuids, String set) {
            double gaps = 0;
            for (int next = 1; next < uuids.size(); next++) {
                gaps += Math.abs(LunarLanding.at(0, 0, uuids.get(next)).z()
                        - LunarLanding.at(0, 0, uuids.get(next - 1)).z());
            }
            double meanGap = gaps / (uuids.size() - 1);
            assertTrue(meanGap > LEAST_MEAN_GAP, set + " neighbours landed a mean " + meanGap + " blocks apart");
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

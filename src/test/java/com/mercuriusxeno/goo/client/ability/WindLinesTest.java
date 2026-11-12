package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.WindStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wind line's path: it rushes straight along its axis, swaying slightly,
 * then curls out from the cone in a tight spiral facing back along the look,
 * its head slowing as it closes on the curl's center, which it reaches as
 * the line ends, the tail drawing in to meet it; and a blown line leaves
 * within the cone.
 */
class WindLinesTest {

    private static final double EPSILON = 1e-9;
    private static final int RGB = 0xFFFFFF;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double STRAIGHT = 6;
    private static final WindLines.Sway SWAYING = new WindLines.Sway(UP.scale(WindLines.SWAY),
            SOUTH.scale(WindLines.SWAY), 0.3, 1.1, 0.2, 0.15);

    /**
     * A jet's tailwind rides along with the player, leaving just behind the
     * eye and rushing forward along the look past the camera
     * (decision jet-pushes-along-the-look-while-held).
     */
    @Nested
    class Tailwind {

        private static final double EYE_HEIGHT = 1.62;
        private static final WindStep.Tailwind TAILWIND = new WindStep.Tailwind(6, 30);

        @Test
        void aTailwindLeavesBehindTheEyeAndRushesForwardAlongTheLook() {
            WindLines.Gust gust = WindLines.tailwindGust(TAILWIND, EYE_HEIGHT, EAST);

            assertEquals(EAST, gust.axis());
            assertEquals(-WindLines.TAILWIND_SETBACK, gust.origin().x, EPSILON);
            assertEquals(EYE_HEIGHT, gust.origin().y, EPSILON);
            assertEquals(0, gust.origin().z, EPSILON);
            assertEquals(TAILWIND.range(), gust.range(), EPSILON);
            assertEquals(TAILWIND.coneDegrees(), gust.coneDegrees(), EPSILON);
            assertTrue(gust.carried());
        }

        @Test
        void aCarriedLineRidesWithThePlayerAndAnyOtherStaysInTheWorld() {
            Vec3 rider = new Vec3(10, 64, -5);

            assertEquals(rider, WindLines.anchorOf(line(WindLines.Sway.NONE, true), rider));
            assertEquals(Vec3.ZERO, WindLines.anchorOf(line(WindLines.Sway.NONE, false), rider));
        }

        @Test
        void aCarriedLineOvertakesTheCameraOnItsStraightRun() {
            WindLines.Gust gust = WindLines.tailwindGust(TAILWIND, EYE_HEIGHT, EAST);
            WindLines.Line rushing = new WindLines.Line(gust.origin(), EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT,
                    WindLines.Sway.NONE, 1, 0f, false, 0L, true);

            assertTrue(WindLines.pathPoint(rushing, 0).x < 0);
            assertTrue(WindLines.pathPoint(rushing, WindLines.STRAIGHT_TICKS).x > 0);
        }
    }

    /** A line blown straight along the look, twisting out south first. */
    private static WindLines.Line line(WindLines.Sway sway) {
        return line(sway, false);
    }

    private static WindLines.Line line(WindLines.Sway sway, boolean carried) {
        return new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT, sway, 1, 0f, true, 0L, carried);
    }

    /**
     * Vacuum's wind runs inward: each line starts inside the cone and its head
     * closes on the glove, speeding up as it nears, while every other wind
     * still rushes out (decision vacuum-streaks-wind-lines-inward-to-the-glove).
     */
    @Nested
    class Inward {

        private final WindLines.Line inward = new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT,
                SWAYING, 1, 0f, false, 0L, false, true);

        @Test
        void anInwardLineStartsInsideTheConeAtItsStraightRun() {
            assertEquals(STRAIGHT, WindLines.pathPoint(inward, 0).length(), WindLines.SWAY * 2);
        }

        @Test
        void anInwardLinesHeadClosesOnTheGloveAlongItsLife() {
            for (int tick = 1; tick <= WindLines.LIFE_TICKS; tick++) {
                assertTrue(WindLines.pathPoint(inward, tick).length() < WindLines.pathPoint(inward, tick - 1).length(),
                        "tick " + tick + " should stand nearer the glove");
            }
            assertEquals(0, WindLines.pathPoint(inward, WindLines.LIFE_TICKS).length(), EPSILON);
        }

        @Test
        void anInwardLineSpeedsUpAsItNearsTheGlove() {
            double early = closing(2);
            double late = closing(WindLines.LIFE_TICKS - 1);
            assertTrue(late > early, "the head should close faster near the glove: " + early + " then " + late);
        }

        @Test
        void anOutwardLinesHeadDrawsAwayFromTheGloveAlongItsLife() {
            WindLines.Line outward = line(WindLines.Sway.NONE);
            for (int tick = 1; tick <= WindLines.LIFE_TICKS; tick++) {
                assertTrue(WindLines.pathPoint(outward, tick).length() > WindLines.pathPoint(outward, tick - 1).length(),
                        "tick " + tick + " should stand farther from the glove");
            }
        }

        @Test
        void theWindStepReadsInwardOnVacuumAlone() {
            assertTrue(windOf("void_vacuum").inward(), "Vacuum's wind runs inward");
            assertFalse(windOf("frost_cold").inward(), "Cold's wind runs out");
            assertFalse(windOf("typhoon_gust").inward(), "Gust's wind runs out");
        }

        // vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
        @Test
        void anInwardLineRunsItsCourseWithNoSway() {
            WindLines.Line steady = new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT,
                    WindLines.Sway.NONE, 1, 0f, false, 0L, false, true);
            for (int tick = 0; tick <= WindLines.LIFE_TICKS; tick++) {
                assertEquals(0, WindLines.pathPoint(inward, tick).distanceTo(WindLines.pathPoint(steady, tick)),
                        EPSILON, "tick " + tick + " sways off course");
            }
        }

        @Test
        void anOutwardLineStillSways() {
            double most = 0;
            for (int tick = 0; tick <= WindLines.LIFE_TICKS; tick++) {
                most = Math.max(most, WindLines.pathPoint(line(SWAYING), tick)
                        .distanceTo(WindLines.pathPoint(line(WindLines.Sway.NONE), tick)));
            }
            assertTrue(most > EPSILON, "an outward line should sway off its course");
        }

        @Test
        void anInwardLineRunsTheWholeOfItsGustsRange() {
            for (double range : new double[] {8, 5}) {
                double run = WindLines.runOf(range, true, 0.3);
                WindLines.Line spanning = new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), run,
                        WindLines.Sway.NONE, 1, 0f, false, 0L, false, true);

                assertEquals(range, run, EPSILON);
                assertEquals(range, WindLines.pathPoint(spanning, 0).length(), EPSILON);
                assertEquals(0, WindLines.pathPoint(spanning, WindLines.LIFE_TICKS).length(), EPSILON);
            }
        }

        @Test
        void anInwardLineTurnsGentlyOneWayAboutTheConesAxis() {
            double about = 0.7;
            Vec3 outward = WindLines.radial(EAST, about);
            Vec3 across = EAST.cross(outward).normalize();
            WindLines.Line tilted = new WindLines.Line(Vec3.ZERO, WindLines.tilt(EAST, Math.toRadians(15), about),
                    outward, across, STRAIGHT, WindLines.Sway.NONE, 1, 0f, false, 0L, false, true);
            double start = angleAbout(tilted, 0, outward, across);
            double last = start;
            double direction = 0;
            for (int tick = 1; tick < WindLines.LIFE_TICKS; tick++) {
                double angle = angleAbout(tilted, tick, outward, across);
                double turned = angle - last;
                assertTrue(direction == 0 || Math.signum(turned) == direction, "tick " + tick + " turns back");
                direction = direction == 0 ? Math.signum(turned) : direction;
                last = angle;
            }
            double total = Math.abs(last - start);
            assertTrue(total > 0 && total <= Math.PI / 2, "the twist turns " + Math.toDegrees(total) + " degrees");
        }

        private double closing(int tick) {
            return WindLines.pathPoint(inward, tick - 1).length() - WindLines.pathPoint(inward, tick).length();
        }

        private static double angleAbout(WindLines.Line line, int tick, Vec3 outward, Vec3 across) {
            Vec3 head = WindLines.pathPoint(line, tick);
            return Math.atan2(head.dot(across), head.dot(outward));
        }

        @Test
        void aLineDrawsBetweenTheTwoColorsItCarries() {
            WindStep.LineColors voidPair = new WindStep.LineColors(0x7A52B0, 0xB48CE8);
            for (WindStep.LineColors colors : new WindStep.LineColors[] {WindStep.LineColors.WIND, voidPair}) {
                assertEquals(colors.light(), WindLines.trailColor(colored(0f, colors), 0f, 0f) & RGB);
                assertEquals(colors.dark(), WindLines.trailColor(colored(1f, colors), 0f, 0f) & RGB);
            }
        }

        @Test
        void vacuumsLinesCarryColorsOfTheirOwnAndColdsAndGustsTheWinds() {
            assertTrue(!WindStep.LineColors.WIND.equals(windOf("void_vacuum").colors()), "Vacuum draws the wind's white");
            assertEquals(WindStep.LineColors.WIND, windOf("frost_cold").colors());
            assertEquals(WindStep.LineColors.WIND, windOf("typhoon_gust").colors());
        }

        @Test
        void aVoidMoteLeavesFromTheLineAndEndsAtTheGlove() {
            Vec3 glove = new Vec3(2, 64, -3);
            WindLines.Line far = new WindLines.Line(glove, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT,
                    WindLines.Sway.NONE, 1, 0f, false, 0L, false, true);
            for (int age : new int[] {5, 20, 40}) {
                WindLines.MoteFlight flight = WindLines.moteFlight(far, age);
                Vec3 toGlove = glove.subtract(flight.from());

                assertEquals(0, flight.from().distanceTo(WindLines.pathPoint(far, age)), EPSILON);
                assertEquals(1, flight.velocity().normalize().dot(toGlove.normalize()), EPSILON);
                double carried = (1 - Math.pow(WindLines.MOTE_FRICTION, flight.ticks())) / (1 - WindLines.MOTE_FRICTION);
                assertEquals(0, flight.from().add(flight.velocity().scale(carried)).distanceTo(glove), 1e-6,
                        "a mote should end its flight at the glove");
            }
        }

        /**
         * The client blows a stream's wind through the ability's area, so an
         * ability naming none blows lines of no length and motes that never
         * leave the glove; Vacuum's area spans its own stream's reach and cone.
         * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
         */
        @Test
        void vacuumsWindBlowsThroughItsStreamsReachAndCone() {
            AbilityDefinition vacuum = AbilityJson.decode("void_vacuum");

            assertEquals(vacuum.delivery().range(), vacuum.area().size(), EPSILON, "the wind's reach");
            assertEquals(vacuum.delivery().coneDegrees(), vacuum.area().angle(), EPSILON, "the wind's cone");
        }

        @Test
        void voidMotesRideVacuumsLinesAloneAndSnowflakesColdsAlone() {
            assertTrue(windOf("void_vacuum").voidMotes() && !windOf("void_vacuum").snowflakes());
            assertTrue(windOf("frost_cold").snowflakes() && !windOf("frost_cold").voidMotes());
            assertFalse(windOf("typhoon_gust").snowflakes() || windOf("typhoon_gust").voidMotes());
        }

        private static WindLines.Line colored(float gray, WindStep.LineColors colors) {
            return new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT, WindLines.Sway.NONE, 1,
                    gray, false, 0L, false, true, colors, false);
        }

        private static WindStep windOf(String ability) {
            return AbilityJson.decode(ability).behaviors().stream().filter(WindStep.class::isInstance)
                    .map(WindStep.class::cast).findFirst().orElseThrow();
        }
    }

    @Nested
    class Path {

        @Test
        void theHeadRushesStraightAlongItsAxisFirst() {
            Vec3 head = WindLines.pathPoint(line(WindLines.Sway.NONE), WindLines.STRAIGHT_TICKS / 2);
            assertEquals(STRAIGHT * WindLines.launched(0.5), head.x, EPSILON);
            assertEquals(0, Math.hypot(head.y, head.z), EPSILON);
        }

        // cold-wind-thickens-launches-faster-and-roars
        @Test
        void theHeadLaunchesFasterThanItsAveragePaceThenSlows() {
            double step = 1e-6;
            assertEquals(1 + WindLines.LAUNCH_SURGE, WindLines.launched(step) / step, 1e-4);
            assertEquals(1, WindLines.launched(1), EPSILON);
            assertTrue(WindLines.launched(1) - WindLines.launched(1 - step) < step, "it slows before it curls");
        }

        /** The most the head's heading may turn from one tick to the next: no corner, only a gentle bend. */
        private static final double MOST_TURN_PER_TICK = Math.toRadians(8);
        /** The most the head's heading may stand off its axis anywhere: a slight twist, never square to it. */
        private static final double MOST_OFF_AXIS = Math.toRadians(45);

        @Test
        void theHeadNeverTurnsACorner() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            for (int tick = 1; tick < WindLines.LIFE_TICKS; tick++) {
                Vec3 before = heading(line, tick - 1);
                Vec3 after = heading(line, tick);
                double turn = Math.acos(Math.clamp(before.dot(after), -1, 1));
                assertTrue(turn < MOST_TURN_PER_TICK, "tick " + tick + " turns " + Math.toDegrees(turn) + " degrees");
            }
        }

        @Test
        void theHeadKeepsHeadingAlongItsAxisAsItTwists() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            for (int tick = 0; tick < WindLines.LIFE_TICKS; tick++) {
                double offAxis = Math.acos(Math.clamp(heading(line, tick).dot(EAST), -1, 1));
                assertTrue(offAxis < MOST_OFF_AXIS, "tick " + tick + " heads " + Math.toDegrees(offAxis) + " off");
            }
        }

        @Test
        void theTwistStaysSlight() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            Vec3 end = WindLines.pathPoint(line, WindLines.LIFE_TICKS);
            assertEquals(WindLines.TWIST_RADIUS, Math.hypot(end.y, end.z), EPSILON);
            assertTrue(WindLines.TWIST_TURNS < 1, "less than a turn");
        }

        @Test
        void theHeadSlowsAsItTwistsButStillAdvances() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            double rushing = WindLines.pathPoint(line, 2).distanceTo(WindLines.pathPoint(line, 1));
            double fading = WindLines.pathPoint(line, WindLines.LIFE_TICKS)
                    .distanceTo(WindLines.pathPoint(line, WindLines.LIFE_TICKS - 1));
            assertTrue(fading < rushing, "the head slows as it twists");
            assertTrue(WindLines.pathPoint(line, WindLines.LIFE_TICKS).x
                    > WindLines.pathPoint(line, WindLines.LIFE_TICKS - 1).x, "it still advances as it fades");
        }

        private static Vec3 heading(WindLines.Line line, double tick) {
            return WindLines.pathPoint(line, tick + 1).subtract(WindLines.pathPoint(line, tick)).normalize();
        }
    }

    @Nested
    class Sway {

        @Test
        void aLineLeavesTheGloveOnCourse() {
            assertEquals(0, WindLines.swayAt(line(SWAYING), 0).length(), EPSILON);
        }

        @Test
        void theSwayStaysSlight() {
            for (int tick = 0; tick <= WindLines.LIFE_TICKS; tick++) {
                assertTrue(WindLines.swayAt(line(SWAYING), tick).length() <= 2 * WindLines.SWAY + EPSILON);
            }
        }

        @Test
        void theSwayGrowsFromTheGlove() {
            double near = WindLines.swayAt(line(SWAYING), 1).length();
            double maxNear = 2 * WindLines.SWAY / WindLines.STRAIGHT_TICKS;
            assertTrue(near <= maxNear + EPSILON, "near the glove the sway is a sliver of its reach");
        }
    }

    @Nested
    class Tail {

        @Test
        void theTailTrailsWholeWhileTheLineRushesStraight() {
            assertEquals(WindLines.TAIL_TICKS, WindLines.tailLag(WindLines.STRAIGHT_TICKS), EPSILON);
        }

        @Test
        void theTailMeetsTheHeadAsTheLineEnds() {
            assertEquals(0, WindLines.tailLag(WindLines.LIFE_TICKS), EPSILON);
            assertTrue(WindLines.tailLag(WindLines.LIFE_TICKS - 2) < WindLines.TAIL_TICKS);
        }
    }

    @Test
    void aTiltedLineLeavesWithinItsAngleOfTheLook() {
        double off = Math.toRadians(8);
        Vec3 tilted = WindLines.tilt(EAST, off, 1.3);
        assertEquals(Math.cos(off), tilted.dot(EAST), EPSILON);
        assertEquals(1, tilted.length(), EPSILON);
    }

    // cold-wind-thins-slows-and-coils: few lines, a slow head, a tight coil at no faster a spin
    @Nested
    class Pace {

        /** The last spin: one turn over twelve ticks. */
        private static final double LAST_TURN_PER_TICK = 2 * Math.PI / 12;
        /** The earlier straight run: 60% of an 18-tick life, its head crossing it at an even pace. */
        private static final double EARLIER_STRAIGHT_TICKS = 18 * 0.6;

        @Test
        void aboutFourInTenHeldTicksBlowALine() {
            int blown = 0;
            for (long tick = 1; tick <= 100; tick++) {
                blown += WindLines.blowsOn(tick) ? 1 : 0;
            }
            assertEquals(45, blown);
        }

        // cold-wind-slows-down, cold-wind-thins-slows-and-coils
        @Test
        void theHeadTravelsSlowerEachTickThanTheEarlierEvenPace() {
            double earlierShareEachTick = 1 / EARLIER_STRAIGHT_TICKS;
            for (int tick = 0; tick < WindLines.STRAIGHT_TICKS; tick++) {
                double travelled = WindLines.launched((tick + 1) / WindLines.STRAIGHT_TICKS)
                        - WindLines.launched(tick / WindLines.STRAIGHT_TICKS);
                assertTrue(travelled < earlierShareEachTick, "tick " + tick + " travels " + travelled);
            }
        }

        @Test
        void aLineLivesFiftyTicks() {
            assertEquals(50, WindLines.LIFE_TICKS);
        }

        @Test
        void theTwistSpinsNoFasterThanTheLastCoil() {
            assertTrue(WindLines.TWIST_TURNS * 2 * Math.PI / WindLines.TWIST_TICKS <= LAST_TURN_PER_TICK,
                    "no faster a spin");
        }
    }

    // jet-pushes-along-the-look-while-held
    @Test
    void aJetsWindWhistlesHigherTheFasterThePlayerMoves() {
        assertEquals(WindLines.WIND_PITCH, WindLines.pitchAt(0), 1e-6);
        assertTrue(WindLines.pitchAt(1.2) > WindLines.pitchAt(0.4));
        assertEquals(WindLines.MAX_PITCH, WindLines.pitchAt(10), 1e-6);
    }

    // cold-wind-fades-on-release
    @Test
    void theWindFadesEvenlyToNothingOnceLetGo() {
        assertEquals(WindLines.WIND_VOLUME, WindLines.windVolume(0), 1e-6);
        assertEquals(WindLines.WIND_VOLUME * (1 - 5f / WindLines.WIND_FADE_TICKS), WindLines.windVolume(5), 1e-6);
        assertTrue(WindLines.windVolume(1) < WindLines.WIND_VOLUME && WindLines.windVolume(1) > 0);
        assertEquals(0, WindLines.windVolume(WindLines.WIND_FADE_TICKS), 1e-6);
    }
}

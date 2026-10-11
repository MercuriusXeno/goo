package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.WindStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

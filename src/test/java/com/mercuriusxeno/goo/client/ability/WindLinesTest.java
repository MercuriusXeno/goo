package com.mercuriusxeno.goo.client.ability;

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

    /** A line blown straight along the look, curling south. */
    private static WindLines.Line line(WindLines.Sway sway) {
        return new WindLines.Line(Vec3.ZERO, EAST, SOUTH, EAST.cross(SOUTH), STRAIGHT, sway, 1, 0f, true, 0L);
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

        @Test
        void theCurlLiesOutFromTheCone() {
            Vec3 center = WindLines.curlCenter(line(WindLines.Sway.NONE));
            assertEquals(WindLines.CURL_RADIUS, center.dot(SOUTH), EPSILON);
        }

        @Test
        void theCurlFacesBackAlongTheLook() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            Vec3 normal = line.outward().cross(WindLines.curlForward(line)).normalize();
            assertEquals(Math.cos(WindLines.CURL_TILT), Math.abs(normal.dot(EAST)), 1e-6);
        }

        @Test
        void theCurlWindsInwardToItsCenterAsTheLineEnds() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            Vec3 center = WindLines.curlCenter(line);
            double early = WindLines.pathPoint(line, WindLines.STRAIGHT_TICKS + 1).distanceTo(center);
            double late = WindLines.pathPoint(line, WindLines.LIFE_TICKS - 1).distanceTo(center);
            assertTrue(late < early, "the curl closes in on its center");
            assertEquals(0, WindLines.pathPoint(line, WindLines.LIFE_TICKS).distanceTo(center), EPSILON);
        }

        @Test
        void theHeadSlowsAsItWindsIn() {
            WindLines.Line line = line(WindLines.Sway.NONE);
            double rushing = WindLines.pathPoint(line, 2).distanceTo(WindLines.pathPoint(line, 1));
            double closing = WindLines.pathPoint(line, WindLines.LIFE_TICKS)
                    .distanceTo(WindLines.pathPoint(line, WindLines.LIFE_TICKS - 1));
            assertTrue(closing < rushing, "the head slows in its curl");
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

        @Test
        void aboutFourInTenHeldTicksBlowALine() {
            int blown = 0;
            for (long tick = 1; tick <= 100; tick++) {
                blown += WindLines.blowsOn(tick) ? 1 : 0;
            }
            assertEquals(45, blown);
        }

        @Test
        void aLineLivesFiftyTicks() {
            assertEquals(50, WindLines.LIFE_TICKS);
        }

        @Test
        void theCurlCoilsTighterAndLongerWithoutSpinningFaster() {
            double curlTicks = WindLines.LIFE_TICKS - WindLines.STRAIGHT_TICKS;
            assertEquals(WindLines.LIFE_TICKS / 2.0, curlTicks, EPSILON);
            assertTrue(WindLines.CURL_TURNS >= 2 && WindLines.CURL_RADIUS < 0.3);
            assertTrue(WindLines.CURL_TURNS * 2 * Math.PI / curlTicks <= LAST_TURN_PER_TICK, "no faster a spin");
        }
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

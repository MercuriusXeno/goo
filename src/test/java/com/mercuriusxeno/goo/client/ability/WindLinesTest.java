package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wind line's path: it rushes straight along its axis, then curls inward
 * in a tight spiral, its head slowing as it closes on the curl's center,
 * which it reaches as the line ends, the tail drawing in to meet it; and a
 * blown line leaves within the cone.
 */
class WindLinesTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double STRAIGHT = 6;

    private static WindLines.Line line(WindLines.Drift drift) {
        return new WindLines.Line(ORIGIN, EAST, SOUTH, UP, STRAIGHT, drift, 0, 0f, true, 0L);
    }

    @Nested
    class Path {

        @Test
        void theHeadRushesStraightAlongItsAxisFirst() {
            Vec3 head = WindLines.pathPoint(line(WindLines.Drift.UP), WindLines.STRAIGHT_TICKS / 2);
            assertEquals(STRAIGHT / 2, head.x, EPSILON);
            assertEquals(0, Math.hypot(head.y, head.z), EPSILON);
        }

        @Test
        void theCurlWindsInwardToItsCenterAsTheLineEnds() {
            WindLines.Line up = line(WindLines.Drift.UP);
            Vec3 center = WindLines.curlCenter(up);
            double early = WindLines.pathPoint(up, WindLines.STRAIGHT_TICKS + 1).distanceTo(center);
            double late = WindLines.pathPoint(up, WindLines.LIFE_TICKS - 1).distanceTo(center);
            assertTrue(late < early, "the curl closes in on its center");
            assertEquals(0, WindLines.pathPoint(up, WindLines.LIFE_TICKS).distanceTo(center), EPSILON);
        }

        @Test
        void theCurlIsTight() {
            WindLines.Line up = line(WindLines.Drift.UP);
            assertEquals(WindLines.CURL_RADIUS,
                    WindLines.pathPoint(up, WindLines.STRAIGHT_TICKS).distanceTo(WindLines.curlCenter(up)), EPSILON);
        }

        @Test
        void theHeadSlowsAsItWindsIn() {
            WindLines.Line out = line(WindLines.Drift.OUT);
            double rushing = WindLines.pathPoint(out, 2).distanceTo(WindLines.pathPoint(out, 1));
            double closing = WindLines.pathPoint(out, WindLines.LIFE_TICKS)
                    .distanceTo(WindLines.pathPoint(out, WindLines.LIFE_TICKS - 1));
            assertTrue(closing < rushing, "the head slows in its curl");
        }

        @Test
        void aLineCurledUpEndsAboveOneCurledDown() {
            assertTrue(WindLines.curlCenter(line(WindLines.Drift.UP)).y
                    > WindLines.curlCenter(line(WindLines.Drift.DOWN)).y);
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
}

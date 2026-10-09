package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wind line's path: it rushes straight along its axis, then spirals away
 * from it, widening and drifting the way it was blown, and a blown line
 * leaves within the cone.
 */
class WindLinesTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 ORIGIN = Vec3.ZERO;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double STRAIGHT = 3;

    private static WindLines.Line line(WindLines.Drift drift) {
        return new WindLines.Line(ORIGIN, EAST, SOUTH, UP, STRAIGHT, drift, 0, 0f, true, 0L);
    }

    /** The head's distance from the line's axis. */
    private static double offAxis(Vec3 point) {
        return Math.hypot(point.y, point.z);
    }

    @Nested
    class Path {

        @Test
        void theHeadRushesStraightAlongItsAxisFirst() {
            double age = STRAIGHT / WindLines.SPEED / 2;
            Vec3 head = WindLines.pathPoint(line(WindLines.Drift.UP), age);
            assertEquals(age * WindLines.SPEED, head.x, EPSILON);
            assertEquals(0, offAxis(head), EPSILON);
        }

        @Test
        void pastItsStraightRunTheHeadSpiralsFartherFromTheAxis() {
            double turned = STRAIGHT / WindLines.SPEED;
            double near = offAxis(WindLines.pathPoint(line(WindLines.Drift.OUT), turned + 2));
            double far = offAxis(WindLines.pathPoint(line(WindLines.Drift.OUT), turned + 8));
            assertTrue(near > 0, "the spiral leaves the axis");
            assertTrue(far > near, "the spiral widens as it goes");
        }

        @Test
        void aLineBlownUpDriftsAboveOneBlownDown() {
            double late = STRAIGHT / WindLines.SPEED + WindLines.LIFE_TICKS;
            assertTrue(WindLines.pathPoint(line(WindLines.Drift.UP), late).y
                    > WindLines.pathPoint(line(WindLines.Drift.DOWN), late).y);
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

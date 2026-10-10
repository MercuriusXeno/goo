package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An updraft's column lifts what stands inside its width from its floor up
 * to below its top and leaves the rest alone; a lift raises a rise to the
 * column's speed and keeps the sideways motion
 * (decision updraft-blob-stands-a-column-of-wind).
 */
class EntityLiftTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BASE = new Vec3(2.5, 1, 2.5);
    /** typhoon_updraft.json's column. */
    private static final double RADIUS = 1;
    private static final double HEIGHT = 8;
    private static final double SPEED = 0.4;

    @Nested
    class Bounds {

        @Test
        void aMobStandingBesideTheBlobInsideTheWidthIsInTheColumn() {
            assertTrue(EntityLift.inColumn(new Vec3(3.5, 1, 2.5), BASE, RADIUS, HEIGHT));
        }

        @Test
        void aMobJustBelowTheTopIsInAndOneAtTheTopIsOut() {
            assertTrue(EntityLift.inColumn(BASE.add(0, HEIGHT - 0.01, 0), BASE, RADIUS, HEIGHT));
            assertFalse(EntityLift.inColumn(BASE.add(0, HEIGHT, 0), BASE, RADIUS, HEIGHT));
        }

        @Test
        void aMobBesideTheColumnOrBelowItsFloorIsOut() {
            assertFalse(EntityLift.inColumn(BASE.add(RADIUS + 0.01, 1, 0), BASE, RADIUS, HEIGHT));
            assertFalse(EntityLift.inColumn(BASE.add(0, 1, -RADIUS - 0.01), BASE, RADIUS, HEIGHT));
            assertFalse(EntityLift.inColumn(BASE.add(0, -0.01, 0), BASE, RADIUS, HEIGHT));
        }

        @Test
        void theBoxTheScanReadsHoldsTheWholeColumn() {
            var box = EntityLift.columnBox(BASE, RADIUS, HEIGHT);

            assertEquals(BASE.x - RADIUS, box.minX, EPSILON);
            assertEquals(BASE.y, box.minY, EPSILON);
            assertEquals(BASE.z + RADIUS, box.maxZ, EPSILON);
            assertEquals(BASE.y + HEIGHT, box.maxY, EPSILON);
        }
    }

    @Nested
    class Lift {

        @Test
        void aFallTurnsIntoTheColumnsRiseAndTheSidewaysMotionStays() {
            Vec3 lifted = EntityLift.lifted(new Vec3(0.1, -0.6, -0.2), SPEED);

            assertEquals(new Vec3(0.1, SPEED, -0.2), lifted);
        }

        @Test
        void aFasterRiseIsKept() {
            assertEquals(0.9, EntityLift.lifted(new Vec3(0, 0.9, 0), SPEED).y, EPSILON);
        }
    }
}

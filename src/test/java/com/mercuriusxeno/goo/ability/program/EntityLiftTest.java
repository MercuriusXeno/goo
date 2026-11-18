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
 * column's speed, cut near the top to land on it, and keeps the sideways
 * motion.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 */
class EntityLiftTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BASE = new Vec3(2.5, 1, 2.5);
    /** A column a block wide each way, eight blocks tall. */
    private static final double RADIUS = 1;
    private static final double HEIGHT = 8;
    private static final double SPEED = 0.4;
    private static final double FALL_CAP = 0.15;

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

        @Test
        void aRiseNearTheTopIsCutToWhatIsLeftOfTheColumn() {
            double left = 0.1;

            assertEquals(new Vec3(0.1, left, -0.2), EntityLift.risen(new Vec3(0.1, -0.6, -0.2), SPEED, left));
        }

        @Test
        void aFallOverThePushIsCappedAtTheFallCapKeepingItsSidewaysMotion() {
            Vec3 carried = EntityLift.carried(new Vec3(0.1, -0.6, -0.2), -2, SPEED, FALL_CAP);

            assertEquals(new Vec3(0.1, -FALL_CAP, -0.2), carried);
        }

        @Test
        void aRiseOverThePushStays() {
            assertEquals(0.3, EntityLift.carried(new Vec3(0, 0.3, 0), -1, SPEED, FALL_CAP).y, EPSILON);
        }

        @Test
        void aFallInsideThePushTurnsIntoTheRise() {
            assertEquals(SPEED, EntityLift.carried(new Vec3(0, -0.6, 0), HEIGHT, SPEED, FALL_CAP).y, EPSILON);
        }

        @Test
        void aRiseFarBelowTheTopKeepsTheColumnsSpeed() {
            assertEquals(SPEED, EntityLift.risen(new Vec3(0, -0.6, 0), SPEED, HEIGHT).y, EPSILON);
        }
    }
}

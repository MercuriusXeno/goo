package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.throwing.ConeShape;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vacuum's pull: an item gains an acceleration toward the eye; a mob is
 * dragged at a steady pace that knockback resistance slows, closing on the
 * player until about a block in front; bosses and players are refused
 * (decision vacuum-pulls-items-to-pickup-and-drags-mobs).
 */
class VacuumPullStepTest {

    private static final double PACE = 0.2;
    private static final double STOP = 1.5;
    private static final double CLOSE = 1e-9;
    private static final Vec3 PLAYER = new Vec3(0, 1, 0);

    /**
     * The shipped pull keeps its speeds whatever Vacuum's cone is tuned to,
     * the item pull and mob drag taking the cone at those speeds
     * (decision vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave).
     */
    @Nested
    class Shipped {

        @Test
        void theShippedPullKeepsItsAccelerationTopSpeedPaceAndStop() {
            VacuumPullStep shipped = AbilityJson.decode("void_vacuum").behaviors().stream()
                    .filter(VacuumPullStep.class::isInstance).map(VacuumPullStep.class::cast).findFirst().orElseThrow();

            assertEquals(new VacuumPullStep(0.1, 0.5, PACE, STOP), shipped);
        }
    }

    @Nested
    class Pace {

        @Test
        void knockbackResistanceSlowsTheDrag() {
            assertTrue(VacuumPullStep.pace(PACE, 0.5) < VacuumPullStep.pace(PACE, 0));
        }

        @Test
        void aMobWithNoKnockbackResistanceIsDraggedAtTheFullPace() {
            assertEquals(PACE, VacuumPullStep.pace(PACE, 0), CLOSE);
        }

        @Test
        void aMobWhollyResistingKnockbackIsNotDragged() {
            assertEquals(0, VacuumPullStep.pace(PACE, 1), CLOSE);
        }
    }

    /**
     * Vacuum's pull on an item adds an acceleration toward the eye to the
     * motion the item already has, no faster than the top speed and no
     * farther than the eye (decision vacuum-pulls-items-to-pickup-and-drags-mobs).
     */
    @Nested
    class ItemPull {

        private static final double ACCELERATION = 0.05;
        private static final double TOP_SPEED = 1;
        private static final Vec3 FAR_EAST = new Vec3(5, 1, 0);

        @Test
        void anItemAtRestGainsTheAccelerationTowardTheEye() {
            Vec3 pulled = VacuumPullStep.pulledVelocity(Vec3.ZERO, FAR_EAST, PLAYER, ACCELERATION, TOP_SPEED);

            assertVelocity(new Vec3(-ACCELERATION, 0, 0), pulled);
        }

        @Test
        void anItemMovingAlongTheLineKeepsItsMotionAndGainsTheAccelerationTowardTheEye() {
            Vec3 moving = new Vec3(-0.3, 0, 0);

            Vec3 pulled = VacuumPullStep.pulledVelocity(moving, FAR_EAST, PLAYER, ACCELERATION, TOP_SPEED);

            assertVelocity(moving.add(-ACCELERATION, 0, 0), pulled);
        }

        @Test
        void anItemMovingAcrossTheLineKeepsHalfThatMotion() {
            Vec3 popping = new Vec3(0, 0.2, 0.1);

            Vec3 pulled = VacuumPullStep.pulledVelocity(popping, FAR_EAST, PLAYER, ACCELERATION, TOP_SPEED);

            assertVelocity(new Vec3(-ACCELERATION, 0.1, 0.05), pulled);
        }

        @Test
        void aFastItemIsHeldToTheTopSpeed() {
            Vec3 pulled = VacuumPullStep.pulledVelocity(new Vec3(-0.98, 0, 0), FAR_EAST, PLAYER, ACCELERATION,
                    TOP_SPEED);

            assertVelocity(new Vec3(-TOP_SPEED, 0, 0), pulled);
        }

        @Test
        void anItemBesideTheEyeMovesNoFartherThanTheEye() {
            Vec3 pulled = VacuumPullStep.pulledVelocity(new Vec3(-0.3, 0, 0), new Vec3(0.2, 1, 0), PLAYER,
                    ACCELERATION, TOP_SPEED);

            assertVelocity(new Vec3(-0.2, 0, 0), pulled);
        }

        private static void assertVelocity(Vec3 expected, Vec3 actual) {
            assertEquals(expected.x, actual.x, CLOSE, () -> "x of " + actual);
            assertEquals(expected.y, actual.y, CLOSE, () -> "y of " + actual);
            assertEquals(expected.z, actual.z, CLOSE, () -> "z of " + actual);
        }
    }

    @Nested
    class Drag {

        @Test
        void aFarMobClosesOnThePlayerAtThePace() {
            Vec3 step = VacuumPullStep.dragStep(new Vec3(5, 1, 0), PLAYER, PACE, STOP);

            assertEquals(-PACE, step.x, CLOSE);
            assertEquals(0, step.z, CLOSE);
        }

        @Test
        void aMobNearTheStopClosesOnlyToTheStop() {
            Vec3 step = VacuumPullStep.dragStep(new Vec3(STOP + 0.1, 1, 0), PLAYER, PACE, STOP);

            assertEquals(-0.1, step.x, CLOSE);
        }

        @Test
        void aMobWithinTheStopIsNotDragged() {
            assertEquals(Vec3.ZERO, VacuumPullStep.dragStep(new Vec3(1, 1, 0), PLAYER, PACE, STOP));
        }

        @Test
        void theDragIsLevelWhateverTheHeights() {
            Vec3 step = VacuumPullStep.dragStep(new Vec3(5, 4, 0), PLAYER, PACE, STOP);

            assertEquals(0, step.y, CLOSE);
        }
    }

    /**
     * The pull reads the shared cone test with the shape it is handed: a cone wide across and thin up and down
     * takes an item off to the side and leaves one the same angle above the axis
     * (decision cone-style-abilities-are-reviewed-for-consistency).
     */
    @Nested
    class Cone {

        private static final Vec3 EYE = new Vec3(0, 1.62, 0);
        private static final Vec3 LOOK = new Vec3(9, 0, 0);
        private static final ConeShape WIDE_AND_THIN = new ConeShape(60, 8, false);
        private static final double HALF_AN_ITEM = 0.125;

        private static AABB itemAt(Vec3 middle) {
            return new AABB(middle, middle).inflate(HALF_AN_ITEM);
        }

        @Test
        void anItemOffToTheSideIsPulledAndOneAsFarAboveIsLeft() {
            // five ahead and two off the axis, about 22 degrees: inside the 30 either side, outside the 4 up
            assertTrue(VacuumPullStep.pulls(EYE, LOOK, WIDE_AND_THIN, itemAt(EYE.add(5, 0, 2))));
            assertFalse(VacuumPullStep.pulls(EYE, LOOK, WIDE_AND_THIN, itemAt(EYE.add(5, 2, 0))));
        }
    }

    @Nested
    class Targets {

        @Test
        void aMobIsDragged() {
            Mob mob = mock(Mob.class);
            when(mob.isAlive()).thenReturn(true);
            when(mob.getType()).thenAnswer(unused -> EntityTypes.ZOMBIE);

            assertTrue(VacuumPullStep.drags(mob));
        }

        @Test
        void aBossIsNotDragged() {
            Mob boss = mock(Mob.class);
            when(boss.isAlive()).thenReturn(true);
            when(boss.getType()).thenAnswer(unused -> EntityTypes.WITHER);

            assertFalse(VacuumPullStep.drags(boss));
        }

        @Test
        void aPlayerIsNotDragged() {
            Player player = mock(Player.class);
            when(player.isAlive()).thenReturn(true);

            assertFalse(VacuumPullStep.drags(player));
        }
    }
}

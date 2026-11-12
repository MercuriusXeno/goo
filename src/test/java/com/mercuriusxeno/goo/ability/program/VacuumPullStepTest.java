package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Vacuum's drag on a mob: a steady pace that knockback resistance slows,
 * closing on the player until about a block in front, and refused to bosses
 * and players (decision vacuum-pulls-items-to-pickup-and-drags-mobs).
 */
class VacuumPullStepTest {

    private static final double PACE = 0.2;
    private static final double STOP = 1.5;
    private static final double CLOSE = 1e-9;
    private static final Vec3 PLAYER = new Vec3(0, 1, 0);

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

    @Nested
    class Targets {

        @Test
        void aMobIsDragged() {
            Mob mob = mock(Mob.class);
            when(mob.isAlive()).thenReturn(true);
            when(mob.getType()).thenAnswer(unused -> EntityType.ZOMBIE);

            assertTrue(VacuumPullStep.drags(mob));
        }

        @Test
        void aBossIsNotDragged() {
            Mob boss = mock(Mob.class);
            when(boss.isAlive()).thenReturn(true);
            when(boss.getType()).thenAnswer(unused -> EntityType.WITHER);

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

package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The shriek calls a hostile mob and passes every other mob over: a monster
 * is called, a creature is not, and an enemy outside the monster category
 * is called whatever its category (decision shriek-pulls-hostiles-only). The
 * shriek plays the sculk shrieker's own shriek and raises its column of ten
 * shriek particles, and a mob it calls walks to the blob's cell
 * (decision shriek-blob-cries-once-and-calls-earshot).
 */
class SculkCallTest {

    private static final BlockPos BLOB = new BlockPos(3, 64, -7);
    private static final int SHRIEKER_COLUMN = 10;
    private static final long SHRIEK_TICK = 12_000;
    private static final int LASTS = 600;

    @Test
    void callsAZombie() {
        assertTrue(SculkCall.calls(MobCategory.MONSTER, true));
    }

    @Test
    void passesACowOver() {
        assertFalse(SculkCall.calls(MobCategory.CREATURE, false));
    }

    @Test
    void callsAnEnemyOutsideTheMonsterCategory() {
        assertTrue(SculkCall.calls(MobCategory.MISC, true));
    }

    @Test
    void theShriekIsTheSculkShriekersOwn() {
        assertEquals(SoundEvents.SCULK_SHRIEKER_SHRIEK, SculkCall.SHRIEK);
    }

    @Test
    void theColumnRisesFromTenShriekParticles() {
        assertEquals(SHRIEKER_COLUMN, SculkCall.COLUMN_PARTICLES);
    }

    @Test
    void aCalledMobWalksToTheBlobsCell() throws ReflectiveOperationException {
        Mob mob = mock(Mob.class);
        Field goals = Mob.class.getDeclaredField("goalSelector");
        goals.setAccessible(true);
        goals.set(mob, new GoalSelector());
        Level level = mock(Level.class);
        when(level.getGameTime()).thenReturn(SHRIEK_TICK);
        when(mob.level()).thenReturn(level);

        SculkCall.call(mob, BLOB, SHRIEK_TICK, LASTS);

        assertTrue(SculkCall.isCalledTo(mob, BLOB));
    }
}

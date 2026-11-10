package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shriek calls a hostile mob and passes every other mob over: a monster
 * is called, a creature is not, and an enemy outside the monster category
 * is called whatever its category (decision shriek-pulls-hostiles-only). The
 * shriek plays the sculk shrieker's own shriek and raises its column of
 * shriek particles, and nothing else, and a mob it calls takes no mob
 * effect (decision shriek-blob-cries-once-and-calls-earshot).
 */
class SculkCallTest {

    private static final BlockPos BLOB = new BlockPos(3, 64, -7);
    private static final double REACH = 16;
    private static final int SHRIEKER_COLUMN = 10;

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

    @SuppressWarnings("unchecked")
    @Test
    void theShriekPlaysTheShriekersShriekAndRaisesItsColumnAndNothingElse() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getEntitiesOfClass(eq(Mob.class), any(AABB.class), any())).thenReturn(List.of());

        SculkCall.shriek(level, BLOB, REACH);

        ArgumentCaptor<SoundEvent> sounds = ArgumentCaptor.forClass(SoundEvent.class);
        verify(level, times(1)).playSound(nullable(Entity.class), anyDouble(), anyDouble(), anyDouble(), sounds.capture(),
                any(SoundSource.class), anyFloat(), anyFloat());
        assertEquals(List.of(SoundEvents.SCULK_SHRIEKER_SHRIEK), sounds.getAllValues());
        ArgumentCaptor<ParticleOptions> particles = ArgumentCaptor.forClass(ParticleOptions.class);
        verify(level, times(SHRIEKER_COLUMN)).sendParticles(particles.capture(), anyDouble(), anyDouble(), anyDouble(),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertTrue(particles.getAllValues().stream().allMatch(ShriekParticleOption.class::isInstance));
    }

    @Test
    void aCalledMobTakesNoMobEffect() throws ReflectiveOperationException {
        Mob mob = mock(Mob.class);
        Field goals = Mob.class.getDeclaredField("goalSelector");
        goals.setAccessible(true);
        goals.set(mob, new GoalSelector());

        SculkCall.call(mob, BLOB);

        assertTrue(SculkCall.isCalledTo(mob, BLOB));
        verify(mob, never()).addEffect(any());
        verify(mob, never()).addEffect(any(), any());
    }
}

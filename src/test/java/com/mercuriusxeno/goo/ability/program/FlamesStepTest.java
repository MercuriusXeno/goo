package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each Flames burn sets a living thing alight before its fire hit lands,
 * so a killing burn drops cooked loot, and spares the fire-immune
 * (decision flames-pixelated-fire-stream), and hurts through the mob's invulnerability window
 * rather than clearing it (decision flames-and-heater-respect-i-frames), and the
 * shipped Flames carries the ticks a block takes to cook under it
 * (decision flames-absorbs-broil-and-cooks-blocks).
 */
class FlamesStepTest {

    private static final float DAMAGE = 1f;
    private static final int BURN_SECONDS = 5;
    /** Ticks into a fresh hit's twenty: above ten, vanilla swallows any hit no harder than the last. */
    private static final int INSIDE_THE_WINDOW = 15;
    /** The ticks of heat blaze_flames.json names for a block to cook. */
    private static final int SHIPPED_COOK_TICKS = 40;

    private final ServerLevel level = mock(ServerLevel.class);
    private final LivingEntity mob = mock(LivingEntity.class);
    private final DamageSource inFire = mock(DamageSource.class);

    FlamesStepTest() {
        DamageSources sources = mock(DamageSources.class);
        when(sources.inFire()).thenReturn(inFire);
        when(mob.damageSources()).thenReturn(sources);
        when(mob.getHealth()).thenReturn(20f);
    }

    @Test
    void aBurnSetsTheMobAlightThenHurtsItWithFire() {
        FlamesStep.burn(level, mob, DAMAGE, BURN_SECONDS);

        InOrder order = inOrder(mob);
        order.verify(mob).igniteForSeconds(BURN_SECONDS);
        order.verify(mob).hurtServer(level, inFire, DAMAGE);
    }

    @Test
    void aBurnInsideTheMobsInvulnerabilityWindowLeavesTheWindowStanding() {
        // decision flames-and-heater-respect-i-frames: the burn hurts through vanilla's window, never clearing it.
        mob.invulnerableTime = INSIDE_THE_WINDOW;

        FlamesStep.burn(level, mob, DAMAGE, BURN_SECONDS);

        assertEquals(INSIDE_THE_WINDOW, mob.invulnerableTime);
        verify(mob).hurtServer(level, inFire, DAMAGE);
        verify(mob, never()).setHealth(anyFloat());
    }

    @Test
    void aFireImmuneMobIsNeitherLitNorHurt() {
        when(mob.fireImmune()).thenReturn(true);

        FlamesStep.burn(level, mob, DAMAGE, BURN_SECONDS);

        verify(mob, never()).igniteForSeconds(anyInt());
        verify(mob, never()).hurtServer(any(), any(), anyFloat());
    }

    @Test
    void theFirstTickAndEveryFourthAfterPulse() {
        assertTrue(FlamesStep.pulsesOn(1, 4));
        assertFalse(FlamesStep.pulsesOn(2, 4));
        assertFalse(FlamesStep.pulsesOn(4, 4));
        assertTrue(FlamesStep.pulsesOn(5, 4));
        assertTrue(FlamesStep.pulsesOn(9, 4));
    }

    @Test
    void aPulseEveryTickPulsesEachTick() {
        assertTrue(FlamesStep.pulsesOn(2, 1));
    }

    @Test
    void theShippedFlamesCooksABlockOverItsCookTicks() {
        Step flames = AbilityJson.decode("blaze_flames").behaviors().getFirst();

        assertEquals(SHIPPED_COOK_TICKS, ((FlamesStep) flames).cookTicks());
    }
}

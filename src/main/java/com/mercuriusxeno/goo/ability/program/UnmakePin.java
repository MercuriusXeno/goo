package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Pins a mob an unmake holds: it stops where it stands and can neither move
 * nor fight back, for a few ticks renewed each tick the unmake holds it, so it
 * goes free on its own once the unmake lets go.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakePin {

    /** Ticks one pin lasts, past the hold's grace so a held mob never slips between stream ticks. */
    static final int PIN_TICKS = 8;
    /** Slowness and weakness strong enough to stop walking and blows. */
    private static final int PIN_STRENGTH = 255;

    private UnmakePin() {
    }

    /**
     * Pins the mob for the next few ticks.
     *
     * @param mob the held mob
     */
    public static void pin(LivingEntity mob) {
        mob.setDeltaMovement(Vec3.ZERO);
        mob.hurtMarked = true;
        mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, PIN_STRENGTH, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PIN_TICKS, PIN_STRENGTH, false, false));
        if (mob instanceof Mob withAi) {
            withAi.getNavigation().stop();
            withAi.setTarget(null);
        }
    }
}

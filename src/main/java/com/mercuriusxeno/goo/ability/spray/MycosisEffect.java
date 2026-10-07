package com.mercuriusxeno.goo.ability.spray;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Goo's own spore poison: it eats at a mob as poison does, a point of
 * health on poison's beat and never the last, but it is no vanilla poison,
 * so the undead that shrug poison off take it too.
 * mycosis-spore-stream-buds-and-poisons
 */
public final class MycosisEffect extends MobEffect {

    /** A dusky spore violet. */
    private static final int SPORE_COLOR = 0x8B6F9E;
    /** Poison's beat at amplifier zero, in ticks, halved per amplifier. */
    private static final int BASE_INTERVAL = 25;
    private static final float DAMAGE_PER_BEAT = 1.0f;

    /** The spore poison. */
    public MycosisEffect() {
        super(MobEffectCategory.HARMFUL, SPORE_COLOR);
    }

    /**
     * Takes a point of health, leaving the last.
     */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.getHealth() > DAMAGE_PER_BEAT) {
            entity.hurtServer(level, entity.damageSources().magic(), DAMAGE_PER_BEAT);
        }
        return true;
    }

    /**
     * Beats as poison beats: every 25 ticks at amplifier zero, halving per
     * amplifier.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = BASE_INTERVAL >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }
}

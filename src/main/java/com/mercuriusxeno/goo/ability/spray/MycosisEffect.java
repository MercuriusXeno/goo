package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Goo's own spore poison: it eats at a mob as poison does, a point of
 * health on poison's beat and never the last, and slows and weakens it by
 * Slowness's and Weakness's measure per level, but it is no vanilla effect,
 * so the undead that shrug poison off take it too and no vanilla effects
 * stack beside it.
 * mycosis-spore-stream-buds-and-poisons
 */
public final class MycosisEffect extends MobEffect {

    /** A dusky spore violet. */
    private static final int SPORE_COLOR = 0x8B6F9E;
    /** Poison's beat at amplifier zero, in ticks, halved per amplifier. */
    private static final int BASE_INTERVAL = 25;
    private static final float DAMAGE_PER_BEAT = 1.0f;
    /** Slowness's measure: a share of movement speed lost per level. */
    private static final double SLOWING_PER_LEVEL = -0.15;
    /** Weakness's measure: attack damage lost per level. */
    private static final double WEAKENING_PER_LEVEL = -4.0;

    /** The spore poison. */
    public MycosisEffect() {
        super(MobEffectCategory.HARMFUL, SPORE_COLOR);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(Goo.MODID, "mycosis_slowing"), SLOWING_PER_LEVEL,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.ATTACK_DAMAGE,
                Identifier.fromNamespaceAndPath(Goo.MODID, "mycosis_weakening"), WEAKENING_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE);
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

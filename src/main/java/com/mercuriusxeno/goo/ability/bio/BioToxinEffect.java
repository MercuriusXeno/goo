package com.mercuriusxeno.goo.ability.bio;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bio's toxin: once a second it takes the share of the mob's max health the
 * throw named, times its amplitude, as magic damage, so it drains a portion
 * of max health rapidly over its duration. Its color is poison's own, so the
 * swirl it raises is poison's.
 * bio-toxin-stacks-to-amplitude-two
 */
public final class BioToxinEffect extends MobEffect {

    /**
     * The counter a toxined mob keeps the share of its max health the toxin
     * takes a second at amplitude one, written by the throw that toxined it.
     */
    public static final Identifier SHARE_PER_SECOND = Identifier.fromNamespaceAndPath(Goo.MODID, "bio_share");
    /** Vanilla poison's color, so the toxin's swirl reads as poison. */
    private static final int POISON_COLOR = 0x87A363;
    private static final int TICKS_PER_SECOND = 20;

    /** The toxin. */
    public BioToxinEffect() {
        super(MobEffectCategory.HARMFUL, POISON_COLOR);
    }

    /**
     * Takes the second's share of max health, times the amplitude.
     */
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        double share = entity.getData(GooAttachments.ENTITY_COUNTERS).read(SHARE_PER_SECOND);
        float damage = ToxinDrain.perSecond(share, entity.getMaxHealth(), amplifier);
        if (damage > 0) {
            entity.hurtServer(level, entity.damageSources().magic(), damage);
        }
        return true;
    }

    /**
     * Beats once a second.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICKS_PER_SECOND == 0;
    }
}

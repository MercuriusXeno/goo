package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The effect-list entry of a goo type's held effect: HeldEffectsEvents lays it
 * while the type's durative ability stands on the glove, its duration the
 * ticks the player's goo pays for, and removes it when the hold ends. The
 * effect itself does nothing each tick.
 * self-effects-trickle-until-ended
 */
public final class GooHeldEffect extends MobEffect {

    /** The swirl color every held effect shows, a goo green. */
    private static final int HELD_EFFECT_COLOR = 0x7FD64B;

    private final ResourceKey<GooTypeDefinition> gooType;

    /**
     * A held effect of one goo type.
     *
     * @param gooType the type whose durative ability the effect shows
     */
    public GooHeldEffect(ResourceKey<GooTypeDefinition> gooType) {
        super(MobEffectCategory.BENEFICIAL, HELD_EFFECT_COLOR);
        this.gooType = gooType;
    }

    /**
     * The goo type whose durative ability this effect shows.
     *
     * @return the type's key
     */
    public ResourceKey<GooTypeDefinition> gooType() {
        return gooType;
    }

    /**
     * The name key every effect name reads, the HUD and the inventory's effect
     * list alike: the granted durative ability's name.
     * decision brew-effect-reads-the-abilitys-own-name
     *
     * @return the lang key
     */
    @Override
    public String getDescriptionId() {
        return HeldEffectName.of(gooType);
    }
}

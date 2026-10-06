package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The effect a goo type's potion carries: its instance's duration is how long
 * the type's self + brew ability holds once drunk. The effect itself does
 * nothing each tick; {@link GooBrewEffectEvents} runs the ability when the
 * effect lands on a player, so a type whose brew ability ships later works
 * with no change here.
 * decision brew-grants-the-self-ability-for-an-hour
 */
public final class GooBrewEffect extends MobEffect {

    /** The swirl color every goo brew shows, a goo green. */
    private static final int BREW_COLOR = 0x7FD64B;

    private final ResourceKey<GooTypeDefinition> gooType;

    /**
     * A brew effect of one goo type.
     *
     * @param gooType the type whose brew ability the effect runs
     */
    public GooBrewEffect(ResourceKey<GooTypeDefinition> gooType) {
        super(MobEffectCategory.BENEFICIAL, BREW_COLOR);
        this.gooType = gooType;
    }

    /**
     * The goo type whose brew ability this effect runs.
     *
     * @return the type's key
     */
    public ResourceKey<GooTypeDefinition> gooType() {
        return gooType;
    }
}

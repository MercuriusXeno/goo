package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionSource;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;

/**
 * Gives each datapack load the abilities and reactions goo read from it, so a
 * reload swaps them with the rest of the load and a server stop drops them
 * (decision type-package-and-per-server-holders).
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesGooMixin implements AbilityRegistrySource, GooReactionSource {

    @Unique
    private volatile AbilityRegistry goo$abilityRegistry = AbilityRegistry.EMPTY;

    @Unique
    private volatile List<GooReaction> goo$reactions = List.of();

    /**
     * {@inheritDoc}
     */
    @Override
    public AbilityRegistry abilityRegistry() {
        return goo$abilityRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void holdAbilityRegistry(AbilityRegistry registry) {
        goo$abilityRegistry = registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GooReaction> gooReactions() {
        return goo$reactions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void holdGooReactions(List<GooReaction> reactions) {
        goo$reactions = reactions;
    }
}

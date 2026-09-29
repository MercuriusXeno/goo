package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.data.GooServerValueHolder;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.registry.GooServerStateHolder;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeOrderSource;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;

/**
 * Gives each server what goo holds for its life: its goo value registry, its
 * state between ticks, and the goo types its registries hold
 * (decision type-package-and-per-server-holders).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerGooStateMixin
        implements GooServerValueHolder, GooServerStateHolder, GooTypeOrderSource {

    @Unique
    private volatile @Nullable GooValueRegistry goo$valueRegistry;

    @Unique
    private final GooServerState goo$serverState = new GooServerState();

    @Unique
    private volatile @Nullable List<ResourceKey<GooTypeDefinition>> goo$typeOrder;

    /**
     * The server's registries, which stand unchanged for its life.
     *
     * @return the registry access
     */
    @Shadow
    public abstract RegistryAccess.Frozen registryAccess();

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable GooValueRegistry gooValueRegistry() {
        return goo$valueRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void holdGooValueRegistry(@Nullable GooValueRegistry registry) {
        goo$valueRegistry = registry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GooServerState gooServerState() {
        return goo$serverState;
    }

    /**
     * Reads the types from the server's registries on first ask and keeps them.
     *
     * @return the keys, in {@link GooTypes#ORDER}
     */
    @Override
    public List<ResourceKey<GooTypeDefinition>> gooTypeOrder() {
        List<ResourceKey<GooTypeDefinition>> order = goo$typeOrder;
        if (order == null) {
            order = GooTypes.all(registryAccess());
            goo$typeOrder = order;
        }
        return order;
    }
}

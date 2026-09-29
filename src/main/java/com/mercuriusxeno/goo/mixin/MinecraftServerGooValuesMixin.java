package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.data.GooServerValueHolder;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives each server a hold on its goo value registry, so the values live and
 * die with the server that derived them (decision type-package-and-per-server-holders).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerGooValuesMixin implements GooServerValueHolder {

    @Unique
    private volatile @Nullable GooValueRegistry goo$valueRegistry;

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
}

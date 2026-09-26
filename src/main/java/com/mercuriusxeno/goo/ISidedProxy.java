package com.mercuriusxeno.goo;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Dist-safe proxy for operations that differ between client and server.
 * The client implementation is installed by {@link com.mercuriusxeno.goo.client.GooClientSetup}
 * (Dist.CLIENT only). Common-side code calls {@link #get()} and never
 * references client classes directly.
 */
public interface ISidedProxy {

    /** No-op server proxy. All methods return safe defaults. */
    ISidedProxy SERVER = new ISidedProxy() {};

    /** The active proxy instance. Client on client dist, SERVER on server. */
    ISidedProxy[] INSTANCE = { SERVER };

    /**
     * Returns the active proxy.
     *
     * @return the active proxy instance
     */
    static ISidedProxy get() {
        return INSTANCE[0];
    }

    /**
     * Returns the current crosshair hit result, or null.
     * Server: always null.
     *
     * @return the crosshair hit result, or null on server
     */
    default @Nullable HitResult getCrosshairHit() {
        return null;
    }

    /**
     * Resolves the local player's aim and sends a glove throw to the server.
     * Server: does nothing, since only the client aims
     * (decision client-handlers-under-client-network).
     *
     * @param player  the local player
     * @param gooType the goo type the glove has selected
     */
    default void sendGloveThrow(Player player, ResourceKey<GooTypeDefinition> gooType) {
    }
}

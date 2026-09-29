package com.mercuriusxeno.goo.data;

import org.jspecify.annotations.Nullable;

/**
 * The server's hold on its goo value registry: a mixin gives every
 * MinecraftServer one, attached when the server starts and dropped when it
 * stops, so no value outlives the server that derived it
 * (decision type-package-and-per-server-holders).
 */
public interface GooServerValueHolder {

    /**
     * Answers the registry the server holds.
     *
     * @return the registry, or null before the server starts and after it stops
     */
    @Nullable GooValueRegistry gooValueRegistry();

    /**
     * Holds a registry on the server, or drops the one it holds.
     *
     * @param registry the registry to hold, or null to drop it
     */
    void holdGooValueRegistry(@Nullable GooValueRegistry registry);
}

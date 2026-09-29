package com.mercuriusxeno.goo.registry;

/**
 * The server's hold on its {@link GooServerState}: a mixin gives every
 * MinecraftServer one for its life (decision type-package-and-per-server-holders).
 */
public interface GooServerStateHolder {

    /**
     * Answers the state this server holds.
     *
     * @return the state
     */
    GooServerState gooServerState();
}

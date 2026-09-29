package com.mercuriusxeno.goo.client.network;

/**
 * The client connection's hold on the abilities its server synced: a mixin
 * gives the connection one, so a disconnect drops them with it
 * (decision type-package-and-per-server-holders).
 */
public interface ClientAbilityConnection {

    /**
     * Answers the abilities the connection last received.
     *
     * @return the abilities, empty before the first sync
     */
    ClientAbilities clientAbilities();

    /**
     * Replaces the abilities the connection holds with a fresh sync.
     *
     * @param abilities the abilities the server sent
     */
    void receiveClientAbilities(ClientAbilities abilities);
}

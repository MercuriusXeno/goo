package com.mercuriusxeno.goo.data;

/**
 * The client connection's hold on the goo values the server synced: it lives
 * as long as the connection does, so a disconnect drops the values with it
 * (decision type-package-and-per-server-holders).
 */
public interface GooValueConnection extends GooValueSource {

    /**
     * Replaces the values the connection holds with a fresh sync.
     *
     * @param table the values the server sent
     */
    void receiveGooValues(GooValueTable table);
}

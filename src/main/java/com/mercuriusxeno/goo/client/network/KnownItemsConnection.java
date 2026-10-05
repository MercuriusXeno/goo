package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.data.KnownItems;

/**
 * The client connection's hold on the items its player knows: a mixin gives
 * the connection one, so a disconnect drops them with it
 * (decision knowledge-capability-remembers-destroyed-items).
 */
public interface KnownItemsConnection {

    /**
     * Answers the items the connection's player knows.
     *
     * @return the known items, empty before the first sync
     */
    KnownItems knownItems();

    /**
     * Replaces the known items the connection holds.
     *
     * @param known the known items the server sent, or the held set after a learning
     */
    void receiveKnownItems(KnownItems known);
}

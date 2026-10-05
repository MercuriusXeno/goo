package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.network.KnownItemsConnection;
import com.mercuriusxeno.goo.data.KnownItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The items the client's player knows, for client code that gates on them
 * (decision knowledge-capability-remembers-destroyed-items).
 */
public final class ClientKnownItems {

    private ClientKnownItems() {
    }

    /**
     * Answers the known items the current connection holds.
     *
     * @return the known items, empty while no connection stands
     */
    public static KnownItems current() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection instanceof KnownItemsConnection source) {
            return source.knownItems();
        }
        return KnownItems.NONE;
    }
}

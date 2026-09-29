package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.data.GooValueSource;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The goo values the client's connection holds, for client code that reads
 * values with no level at hand (decision type-package-and-per-server-holders).
 */
public final class ClientGooValues {

    private ClientGooValues() {
    }

    /**
     * Answers the values the current connection received.
     *
     * @return the values, empty while no connection stands
     */
    public static IGooValueLookup current() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection instanceof GooValueSource source) {
            return source.gooValueLookup();
        }
        return GooValueTable.EMPTY;
    }
}

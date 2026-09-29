package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.data.GooValueConnection;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives the client connection the goo values its server synced, so a
 * disconnect drops them with the connection (decision type-package-and-per-server-holders).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerGooValuesMixin implements GooValueConnection {

    @Unique
    private volatile GooValueTable goo$valueTable = GooValueTable.EMPTY;

    /**
     * {@inheritDoc}
     */
    @Override
    public IGooValueLookup gooValueLookup() {
        return goo$valueTable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receiveGooValues(GooValueTable table) {
        goo$valueTable = table;
    }
}

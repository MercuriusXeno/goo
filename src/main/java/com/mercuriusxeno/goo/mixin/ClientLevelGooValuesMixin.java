package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.data.GooValueSource;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Lets a client level answer the goo values of the connection it belongs to,
 * so common code reaches them by the level (decision type-package-and-per-server-holders).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelGooValuesMixin implements GooValueSource {

    @Shadow
    @Final
    private ClientPacketListener connection;

    /**
     * {@inheritDoc}
     */
    @Override
    public IGooValueLookup gooValueLookup() {
        return ((GooValueSource) connection).gooValueLookup();
    }
}

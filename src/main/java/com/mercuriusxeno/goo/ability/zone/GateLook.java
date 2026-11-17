package com.mercuriusxeno.goo.ability.zone;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a conjured door is drawn with: Zone's and End's door with the End
 * portal's starfield, Tartarus' gate with the nether portal's texture.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 */
public enum GateLook {
    /** The End portal's starfield, Zone's and End's door. */
    STARFIELD,
    /** The nether portal's texture, Tartarus' gate. */
    NETHER_PORTAL;

    /** Sends a look. */
    public static final StreamCodec<ByteBuf, GateLook> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], GateLook::ordinal);
}

package com.mercuriusxeno.goo.ability.zone;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a conjured door is drawn with: Zone's and End's door with the End
 * portal's starfield, Tartarus' gate with the nether portal's texture.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 * Each look stands its door its own distance before the caster: Tartarus'
 * doorway closer to their body and face, as the operator asked.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
public enum GateLook {
    /** The End portal's starfield, Zone's and End's door. */
    STARFIELD(ConjuredDoor.STANDS_OFF),
    /** The nether portal's texture, Tartarus' gate. */
    NETHER_PORTAL(0.75);

    /** Sends a look. */
    public static final StreamCodec<ByteBuf, GateLook> STREAM_CODEC =
            ByteBufCodecs.idMapper(ordinal -> values()[ordinal], GateLook::ordinal);

    private final double standsOff;

    GateLook(double standsOff) {
        this.standsOff = standsOff;
    }

    /**
     * @return how far before the caster's feet a door of this look stands on open ground, in blocks
     */
    public double standsOff() {
        return standsOff;
    }
}

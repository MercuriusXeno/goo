package com.mercuriusxeno.goo.ability.yarn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Herd a player holds: the radius it senses mobs within and leads
 * passive mobs from, and the short distance a led mob stops at. A radius of
 * zero is no Herd.
 * leader-is-zoos-brew-ability
 *
 * @param radius   the blocks around the player Herd reaches
 * @param distance the blocks short of the player a led mob holds at
 */
public record Herd(double radius, double distance) {

    /** No Herd. */
    public static final Herd NONE = new Herd(0, 0);

    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DISTANCE = "distance";

    /** Saves the Herd with the player. */
    public static final MapCodec<Herd> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(Herd::radius),
            Codec.DOUBLE.fieldOf(FIELD_DISTANCE).forGetter(Herd::distance)
    ).apply(inst, Herd::new));

    /** Syncs the Herd to the owning client, which outlines the mobs it senses. */
    public static final StreamCodec<ByteBuf, Herd> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Herd::radius,
            ByteBufCodecs.DOUBLE, Herd::distance,
            Herd::new);

    /**
     * Whether a Herd stands.
     *
     * @return true while the radius reaches past the player
     */
    public boolean stands() {
        return radius > 0;
    }
}

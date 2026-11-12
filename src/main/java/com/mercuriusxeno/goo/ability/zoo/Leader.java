package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Leader a player holds: the radius it senses mobs within and leads
 * passive mobs from, and the short distance a led mob stops at. A radius of
 * zero is no Leader.
 * leader-is-zoos-brew-ability
 *
 * @param radius   the blocks around the player Leader reaches
 * @param distance the blocks short of the player a led mob holds at
 */
public record Leader(double radius, double distance) {

    /** No Leader. */
    public static final Leader NONE = new Leader(0, 0);

    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DISTANCE = "distance";

    /** Saves the Leader with the player. */
    public static final MapCodec<Leader> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(Leader::radius),
            Codec.DOUBLE.fieldOf(FIELD_DISTANCE).forGetter(Leader::distance)
    ).apply(inst, Leader::new));

    /** Syncs the Leader to the owning client, which outlines the mobs it senses. */
    public static final StreamCodec<ByteBuf, Leader> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Leader::radius,
            ByteBufCodecs.DOUBLE, Leader::distance,
            Leader::new);

    /**
     * Whether a Leader stands.
     *
     * @return true while the radius reaches past the player
     */
    public boolean stands() {
        return radius > 0;
    }
}

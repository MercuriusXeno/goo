package com.mercuriusxeno.goo.ability.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The Kin a player holds: the radius it senses mobs within and leads
 * passive mobs from, and the short distance a led mob stops at. A radius of
 * zero is no Kin.
 * leader-is-zoos-brew-ability
 *
 * @param radius   the blocks around the player Kin reaches
 * @param distance the blocks short of the player a led mob holds at
 */
public record Kin(double radius, double distance) {

    /** No Kin. */
    public static final Kin NONE = new Kin(0, 0);

    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DISTANCE = "distance";

    /** Saves the Kin with the player. */
    public static final MapCodec<Kin> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(Kin::radius),
            Codec.DOUBLE.fieldOf(FIELD_DISTANCE).forGetter(Kin::distance)
    ).apply(inst, Kin::new));

    /** Syncs the Kin to the owning client, which outlines the mobs it senses. */
    public static final StreamCodec<ByteBuf, Kin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Kin::radius,
            ByteBufCodecs.DOUBLE, Kin::distance,
            Kin::new);

    /**
     * Whether a Kin stands.
     *
     * @return true while the radius reaches past the player
     */
    public boolean stands() {
        return radius > 0;
    }
}

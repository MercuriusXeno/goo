package com.mercuriusxeno.goo.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * The coat a spray's motes fly onto: the rays the coat casts from the
 * caster's eye along the look, so each client finds the ends the coat's rays
 * reach and flies each mote to one.
 * goop-and-slick-spray-follows-the-coat
 *
 * @param eye   the caster's eye, where the coat's rays start
 * @param look  the caster's look, the coat cone's axis
 * @param range the stream's reach in blocks
 */
public record CoatSpray(Vec3 eye, Vec3 look, double range) {

    /** Stream codec for the motes payload. */
    public static final StreamCodec<FriendlyByteBuf, CoatSpray> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, CoatSpray::eye,
            Vec3.STREAM_CODEC, CoatSpray::look,
            ByteBufCodecs.DOUBLE, CoatSpray::range,
            CoatSpray::new);
}

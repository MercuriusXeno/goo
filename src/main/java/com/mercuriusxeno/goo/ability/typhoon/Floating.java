package com.mercuriusxeno.goo.ability.typhoon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The float a mob holds while Float's levitation lifts it: the game time the
 * float ends at. Saved with the mob and synced to every client drawing it,
 * which pulses the mint platform under its feet.
 * float-blob-levitates-the-mob
 *
 * @param expiresAt the game time the float ends at
 */
public record Floating(long expiresAt) {

    /** No float: the mob stands on nothing of Float's. */
    public static final Floating NONE = new Floating(0L);

    private static final String FIELD_EXPIRES_AT = "expires_at";

    /** Saves the float with the mob. */
    public static final MapCodec<Floating> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.LONG.fieldOf(FIELD_EXPIRES_AT).forGetter(Floating::expiresAt)
    ).apply(inst, Floating::new));

    /** Syncs the float to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Floating> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Floating::expiresAt,
            Floating::new);
}

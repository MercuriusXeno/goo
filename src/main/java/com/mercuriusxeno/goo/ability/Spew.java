package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.LowerCaseEnumCodec;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a charm-like cast's release sends at the mob its press took, named by
 * the delivery block's spews field.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 */
public enum Spew {
    /** The blob itself darts to the mob and envelops it. */
    BLOB,
    /**
     * The held blob spews a vanilla shulker bullet, homing on the mob and
     * levitating it on the hit, a shulker's own attack.
     * operator: "it's literally a shulker attack and not just a spontaneous shulker levitation effect"
     */
    SHULKER_BULLET;

    /** Codec reading the lower-case name. */
    public static final Codec<Spew> CODEC = LowerCaseEnumCodec.of(Spew.class, "spew");

    /** Stream codec carrying the spew by its ordinal. */
    public static final StreamCodec<ByteBuf, Spew> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ordinal -> values()[ordinal], Spew::ordinal);
}

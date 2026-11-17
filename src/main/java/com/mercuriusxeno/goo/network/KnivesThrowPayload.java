package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: left click goes down or lifts during a Knives
 * hold. Held down, it throws a knife along the thrower's look as it goes down
 * and one every 2 ticks after; it names no mob, since a knife seeks none.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-throw-one-per-two-ticks-while-left-click-is-held
 * decision knives-do-not-seek-you-just-throw-them
 *
 * @param down true as left click goes down, false as it lifts
 */
public record KnivesThrowPayload(boolean down) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<KnivesThrowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "knives_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, KnivesThrowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, KnivesThrowPayload::down, KnivesThrowPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: a left click during a Knives hold throws a knife
 * along the thrower's look; it names no mob, since a knife seeks none.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-do-not-seek-you-just-throw-them
 */
public record KnivesThrowPayload() implements CustomPacketPayload {

    /** The one throw payload, which carries nothing. */
    public static final KnivesThrowPayload INSTANCE = new KnivesThrowPayload();

    /** Payload type ID for registration. */
    public static final Type<KnivesThrowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "knives_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, KnivesThrowPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

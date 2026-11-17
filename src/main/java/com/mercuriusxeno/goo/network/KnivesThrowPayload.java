package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Client-to-server payload: a left click during a Knives hold throws the
 * knives at the mobs the hold painted, in painted order.
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-paint-mobs-by-hovering-while-forming
 *
 * @param painted the painted mobs' entity ids, in painted order
 */
public record KnivesThrowPayload(List<Integer> painted) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<KnivesThrowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "knives_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, KnivesThrowPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), KnivesThrowPayload::painted,
            KnivesThrowPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

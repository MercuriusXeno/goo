package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Reap's slashes, a fixed leaf crescent the
 * client draws from the tick it arrives, its bright band sweeping the arc and
 * the arc wiping away. Sent to the players watching the slasher's chunk.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param slash the slash
 */
public record ReapSlashPayload(ReapSlash slash) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSlashPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_slash"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ReapSlashPayload> STREAM_CODEC =
        ReapSlash.STREAM_CODEC.map(ReapSlashPayload::new, ReapSlashPayload::slash);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

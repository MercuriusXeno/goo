package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Reap's slashes, a fixed leaf crescent the
 * client draws from the tick it arrives, with the slasher's id so the
 * slasher's own client draws its slashes for its first-person view. Sent to
 * the players watching the slasher's chunk.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param slash     the slash
 * @param slasherId the entity id of the one slashing
 */
public record ReapSlashPayload(ReapSlash slash, int slasherId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSlashPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_slash"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ReapSlashPayload> STREAM_CODEC = StreamCodec.composite(
            ReapSlash.STREAM_CODEC, ReapSlashPayload::slash,
            ByteBufCodecs.VAR_INT, ReapSlashPayload::slasherId,
            ReapSlashPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

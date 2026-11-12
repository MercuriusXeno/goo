package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Reap's slashes, drawn from the tick it
 * arrives, with the id of its slasher, whom it moves with, and where the
 * slasher's feet stood as it was thrown, where it is drawn once the client
 * holds no such entity. Sent to the players watching the slasher's chunk.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param slash      the slash
 * @param slasherId  the entity id of the one slashing
 * @param thrownFrom where the slasher's feet stood as it was thrown
 */
public record ReapSlashPayload(ReapSlash slash, int slasherId, Vec3 thrownFrom) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSlashPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_slash"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ReapSlashPayload> STREAM_CODEC = StreamCodec.composite(
            ReapSlash.STREAM_CODEC, ReapSlashPayload::slash,
            ByteBufCodecs.VAR_INT, ReapSlashPayload::slasherId,
            Vec3.STREAM_CODEC, ReapSlashPayload::thrownFrom,
            ReapSlashPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

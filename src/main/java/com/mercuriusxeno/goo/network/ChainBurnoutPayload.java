package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a ability block burned out, sent to the players
 * tracking its chunk before the marker can be removed, so each goo type's
 * burnout explosion plays even for a program that finishes the tick it
 * fires (decision elemental-explosion-per-type).
 *
 * @param pos        the marker's block position
 * @param placedFace the ordinal of the face the marker was placed on
 * @param gooTypeId  the goo type's short id
 * @param abilityId  the id of the ability the marker ran
 */
public record ChainBurnoutPayload(BlockPos pos, int placedFace, String gooTypeId,
                                  String abilityId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ChainBurnoutPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "chain_burnout"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ChainBurnoutPayload> STREAM_CODEC =
        StreamCodec.of(ChainBurnoutPayload::encode, ChainBurnoutPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Sends the burnout to every player tracking its chunk; a listener that
     * never negotiated the mod's channels, a gametest's mock player, gets none.
     *
     * @param level the server level the burnout plays in
     */
    public void sendToTracking(ServerLevel level) {
        ChunkWatchers.send(level, pos, this);
    }

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, ChainBurnoutPayload payload) {
        buf.writeBlockPos(payload.pos);
        buf.writeVarInt(payload.placedFace);
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static ChainBurnoutPayload decode(FriendlyByteBuf buf) {
        return new ChainBurnoutPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(),
                buf.readUtf());
    }
}

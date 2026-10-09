package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: the blocks streaming into one player's Unmake
 * drink this tick, sent to everyone watching them.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param playerId  the player's entity id
 * @param streaming the blocks streaming into the glove
 */
public record DrinkPayload(int playerId, List<Streaming> streaming) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<DrinkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "drink"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, DrinkPayload> STREAM_CODEC =
            StreamCodec.of(DrinkPayload::encode, DrinkPayload::decode);

    /**
     * One block streaming into the glove.
     *
     * @param pos   the block
     * @param start the game time it started
     * @param end   the game time its last bead leaves it and it is gone
     */
    public record Streaming(BlockPos pos, long start, long end) {
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, DrinkPayload payload) {
        buf.writeVarInt(payload.playerId);
        buf.writeVarInt(payload.streaming.size());
        for (Streaming streaming : payload.streaming) {
            buf.writeBlockPos(streaming.pos());
            buf.writeVarLong(streaming.start());
            buf.writeVarLong(streaming.end());
        }
    }

    private static DrinkPayload decode(FriendlyByteBuf buf) {
        int playerId = buf.readVarInt();
        int count = buf.readVarInt();
        List<Streaming> streaming = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            streaming.add(new Streaming(buf.readBlockPos(), buf.readVarLong(), buf.readVarLong()));
        }
        return new DrinkPayload(playerId, streaming);
    }
}

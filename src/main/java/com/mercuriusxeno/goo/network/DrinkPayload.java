package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: the blocks on their way into one player's Unmake
 * drink this tick, sent to everyone watching them.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param playerId    the player's entity id
 * @param convergence the point the blocks' chunks converge on and their goo item forms at
 *                    (decision unmake-converges-near-the-unmade-blocks-into-a-goo-item)
 * @param streaming   the blocks on their way to it
 */
public record DrinkPayload(int playerId, Vec3 convergence, List<Streaming> streaming) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<DrinkPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "drink"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, DrinkPayload> STREAM_CODEC =
            StreamCodec.of(DrinkPayload::encode, DrinkPayload::decode);

    /**
     * One block on its way into the glove.
     *
     * @param pos    the block
     * @param picked the game time it was picked, the square leaving the hand for it
     * @param start  the game time the square lands and it starts streaming
     * @param end    the game time it is drained and gone
     */
    public record Streaming(BlockPos pos, long picked, long start, long end) {
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, DrinkPayload payload) {
        buf.writeVarInt(payload.playerId);
        Vec3.STREAM_CODEC.encode(buf, payload.convergence);
        buf.writeVarInt(payload.streaming.size());
        for (Streaming streaming : payload.streaming) {
            buf.writeBlockPos(streaming.pos());
            buf.writeVarLong(streaming.picked());
            buf.writeVarLong(streaming.start());
            buf.writeVarLong(streaming.end());
        }
    }

    private static DrinkPayload decode(FriendlyByteBuf buf) {
        int playerId = buf.readVarInt();
        Vec3 convergence = Vec3.STREAM_CODEC.decode(buf);
        int count = buf.readVarInt();
        List<Streaming> streaming = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            streaming.add(new Streaming(buf.readBlockPos(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
        }
        return new DrinkPayload(playerId, convergence, streaming);
    }
}

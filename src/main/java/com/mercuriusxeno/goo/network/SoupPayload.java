package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-to-client payload: one player's Unmake soup this tick, sent to
 * everyone watching them.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param playerId  the player's entity id
 * @param open      false once the soup has turned into goo items, so the client drops it
 * @param held      whether the hold goes on, the ball riding the player's look; once it ends the
 *                  ball waits where it hung
 * @param ball      where the server last placed the ball's middle
 * @param drunk     the goo the soup holds, each type's amount
 * @param streaming the blocks streaming into it
 */
public record SoupPayload(int playerId, boolean open, boolean held, Vec3 ball,
                          Map<ResourceKey<GooTypeDefinition>, Integer> drunk, List<Streaming> streaming)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<SoupPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "soup"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, SoupPayload> STREAM_CODEC =
            StreamCodec.of(SoupPayload::encode, SoupPayload::decode);

    /**
     * One block streaming into the soup.
     *
     * @param pos   the block
     * @param start the game time it started
     * @param end   the game time it is done
     */
    public record Streaming(BlockPos pos, long start, long end) {
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, SoupPayload payload) {
        buf.writeVarInt(payload.playerId);
        buf.writeBoolean(payload.open);
        buf.writeBoolean(payload.held);
        buf.writeDouble(payload.ball.x);
        buf.writeDouble(payload.ball.y);
        buf.writeDouble(payload.ball.z);
        GooAmounts.write(buf, payload.drunk);
        buf.writeVarInt(payload.streaming.size());
        for (Streaming streaming : payload.streaming) {
            buf.writeBlockPos(streaming.pos());
            buf.writeVarLong(streaming.start());
            buf.writeVarLong(streaming.end());
        }
    }

    private static SoupPayload decode(FriendlyByteBuf buf) {
        int playerId = buf.readVarInt();
        boolean open = buf.readBoolean();
        boolean held = buf.readBoolean();
        Vec3 ball = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Map<ResourceKey<GooTypeDefinition>, Integer> drunk = GooAmounts.read(buf);
        int count = buf.readVarInt();
        List<Streaming> streaming = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            streaming.add(new Streaming(buf.readBlockPos(), buf.readVarLong(), buf.readVarLong()));
        }
        return new SoupPayload(playerId, open, held, ball, drunk, streaming);
    }
}

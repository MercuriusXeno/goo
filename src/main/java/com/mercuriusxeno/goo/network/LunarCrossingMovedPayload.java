package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.LunarCrossingShift;
import com.mercuriusxeno.goo.lunar.LunarCrossingViews;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import java.util.BitSet;
import java.util.Collection;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Client-to-server payload: the client moved the chunks it held over by a crossing's shift, and names each chunk it now
 * holds there, so the server skips resending those alone. The chunks travel as one bit each over the square they span,
 * which keeps the payload small at any view distance.
 * decision moon-wraps-at-its-border-like-the-zone
 *
 * @param chunksX the crossing's shift on x, in chunks
 * @param chunksZ the crossing's shift on z, in chunks
 * @param minX    the square's least chunk x
 * @param minZ    the square's least chunk z
 * @param width   the square's side, in chunks
 * @param held    one bit per chunk of the square, set where the client moved a chunk
 */
public record LunarCrossingMovedPayload(int chunksX, int chunksZ, int minX, int minZ, int width, BitSet held)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<LunarCrossingMovedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_crossing_moved"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, LunarCrossingMovedPayload> STREAM_CODEC =
            StreamCodec.ofMember(LunarCrossingMovedPayload::write, LunarCrossingMovedPayload::read);

    /**
     * The payload naming the chunks a client moved by a crossing's shift.
     *
     * @param shift the crossing's shift
     * @param moved where each chunk the client moved now stands
     * @return the payload
     */
    public static LunarCrossingMovedPayload of(LunarCrossingShift shift, Collection<ChunkPos> moved) {
        int minX = moved.stream().mapToInt(ChunkPos::x).min().orElse(0);
        int minZ = moved.stream().mapToInt(ChunkPos::z).min().orElse(0);
        int maxX = moved.stream().mapToInt(ChunkPos::x).max().orElse(minX);
        int maxZ = moved.stream().mapToInt(ChunkPos::z).max().orElse(minZ);
        int width = Math.max(maxX - minX, maxZ - minZ) + 1;
        BitSet held = new BitSet(width * width);
        for (ChunkPos pos : moved) {
            held.set((pos.z() - minZ) * width + pos.x() - minX);
        }
        return new LunarCrossingMovedPayload(shift.chunksX(), shift.chunksZ(), minX, minZ, width, held);
    }

    private static LunarCrossingMovedPayload read(FriendlyByteBuf buffer) {
        return new LunarCrossingMovedPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBitSet());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(chunksX);
        buffer.writeVarInt(chunksZ);
        buffer.writeVarInt(minX);
        buffer.writeVarInt(minZ);
        buffer.writeVarInt(width);
        buffer.writeBitSet(held);
    }

    /**
     * The crossing's shift this payload confirms.
     *
     * @return the shift
     */
    public LunarCrossingShift shift() {
        return new LunarCrossingShift(chunksX, chunksZ);
    }

    /**
     * The chunks the client moved, each packed as ChunkPos packs it.
     *
     * @return the moved chunks
     */
    public LongSet movedChunks() {
        LongSet moved = new LongOpenHashSet();
        held.stream().forEach(bit -> moved.add(new ChunkPos(minX + bit % width, minZ + bit / width).pack()));
        return moved;
    }

    /**
     * Records the client's move on the server thread.
     *
     * @param payload the payload
     * @param context the network context
     */
    public static void handle(LunarCrossingMovedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LunarCrossingViews.confirmed(player, payload);
            }
        });
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

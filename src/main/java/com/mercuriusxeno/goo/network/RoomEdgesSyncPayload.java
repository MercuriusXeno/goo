package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the edges of the Zone room the player stands
 * in, as linked gate pairs, so the client carries its player across an edge
 * to the opposite side the way it carries it through a portal. Sent as the
 * player enters the room and each time a feed moves its extent, every gate's
 * box in doubles, so a plane partway through a block arrives exactly.
 * outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 *
 * @param edges the room's edge pairs, east with west and south with north
 */
public record RoomEdgesSyncPayload(List<GatePassage.Pair> edges) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<RoomEdgesSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "room_edges_sync"));

    private static final StreamCodec<ByteBuf, GatePassage.Pair> PAIR_CODEC = StreamCodec.composite(
            Gate.STREAM_CODEC, GatePassage.Pair::one,
            Gate.STREAM_CODEC, GatePassage.Pair::other,
            GatePassage.Pair::new);

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, RoomEdgesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            PAIR_CODEC.apply(ByteBufCodecs.list()), RoomEdgesSyncPayload::edges,
            RoomEdgesSyncPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

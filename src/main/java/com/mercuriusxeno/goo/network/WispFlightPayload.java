package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: one tick of a caster's Radiant placed these
 * wisps, so the client flies each out of the caster's glove to its cell.
 * Sent to the players watching the caster and to the caster itself.
 * decision radiant-wisps-where-light-is-low
 * operator ruling 2026-10-10: each wisp flies out of the glove to its spot, in place of motes off the hand
 *
 * @param casterId the casting player's entity id
 * @param cells    the cells the tick placed wisps in
 */
public record WispFlightPayload(int casterId, List<BlockPos> cells) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<WispFlightPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "wisp_flight"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, WispFlightPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WispFlightPayload::casterId,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), WispFlightPayload::cells,
            WispFlightPayload::new);

    /**
     * Copies the cells so the record holds them unmodifiable.
     */
    public WispFlightPayload {
        cells = List.copyOf(cells);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

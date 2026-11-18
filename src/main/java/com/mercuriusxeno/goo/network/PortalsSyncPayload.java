package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the player's own Quantum portals, their pair's
 * blue one and orange one where each stands, and every linked pair in their
 * level, theirs among them, sent whenever any changes and when they join.
 * player-holds-one-pair-of-portals
 * portals-link-by-looking-through-portal-vision
 *
 * @param portals the player's standing portals, blue first
 * @param links   every pair linked in the player's level, so the client sees through each
 */
public record PortalsSyncPayload(List<Portal> portals, List<QuantumPortals.Link> links)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalsSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portals_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortalsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Portal.STREAM_CODEC.apply(ByteBufCodecs.list()), PortalsSyncPayload::portals,
            QuantumPortals.Link.STREAM_CODEC.apply(ByteBufCodecs.list()), PortalsSyncPayload::links,
            PortalsSyncPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

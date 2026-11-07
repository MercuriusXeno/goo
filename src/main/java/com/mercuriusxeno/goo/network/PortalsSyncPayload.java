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
import java.util.Optional;

/**
 * Server-to-client payload: the player's own Quantum portals, the pair they
 * linked and the portal they are choosing a partner for, sent whenever any
 * changes and when they join.
 * portals-link-by-looking-through-portal-vision
 *
 * @param portals  every portal the player placed
 * @param link     the pair they linked, empty for none
 * @param choosing the portal they are choosing a partner for, empty while they choose none
 * @param links    every pair any player linked in the player's level, so the client sees through each
 */
public record PortalsSyncPayload(List<Portal> portals, Optional<QuantumPortals.Link> link,
                                 Optional<Portal> choosing, List<QuantumPortals.Link> links)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalsSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portals_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortalsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Portal.STREAM_CODEC.apply(ByteBufCodecs.list()), PortalsSyncPayload::portals,
            ByteBufCodecs.optional(QuantumPortals.Link.STREAM_CODEC), PortalsSyncPayload::link,
            ByteBufCodecs.optional(Portal.STREAM_CODEC), PortalsSyncPayload::choosing,
            QuantumPortals.Link.STREAM_CODEC.apply(ByteBufCodecs.list()), PortalsSyncPayload::links,
            PortalsSyncPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

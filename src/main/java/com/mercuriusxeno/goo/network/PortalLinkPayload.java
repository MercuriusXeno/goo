package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: while choosing, the player right clicked one of
 * their distant portals, linking it to the one they chose from.
 * portals-link-by-looking-through-portal-vision
 *
 * @param target the portal right clicked as the partner
 */
public record PortalLinkPayload(Portal target) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalLinkPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portal_link"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortalLinkPayload> STREAM_CODEC = StreamCodec.composite(
            Portal.STREAM_CODEC, PortalLinkPayload::target,
            PortalLinkPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the payload on the server thread, for a player holding the glove.
     *
     * @param payload the payload
     * @param context the network context
     */
    public static void handle(PortalLinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && GooThrowHandler.validateGlove(player)) {
                PortalChoice.link(player, payload.target());
            }
        });
    }
}

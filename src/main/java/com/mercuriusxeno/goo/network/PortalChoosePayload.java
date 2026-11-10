package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Client-to-server payload: with Portal in hand the player right clicked one
 * of their portals, entering choosing its partner, or right clicked none of
 * them while choosing, ending it.
 * portals-link-by-looking-through-portal-vision
 *
 * @param portal the portal right clicked, empty to end choosing
 */
public record PortalChoosePayload(Optional<Portal> portal) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalChoosePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portal_choose"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortalChoosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Portal.STREAM_CODEC), PortalChoosePayload::portal,
            PortalChoosePayload::new);

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
    public static void handle(PortalChoosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && GooThrowHandler.validateGlove(player)) {
                PortalChoice.choose(player, payload.portal());
            }
        });
    }
}

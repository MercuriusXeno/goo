package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveButton;
import com.mercuriusxeno.goo.ability.quantum.PortalShots;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: with Portal held, the player shot it by a mouse
 * button, left click for the blue portal and right click for the orange one.
 * portal-takes-over-left-and-right-click-for-blue-and-orange
 *
 * @param button the button pressed
 * @param shot   the cast the client aimed
 */
public record PortalShotPayload(GloveButton button, GooThrowPayload shot) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortalShotPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portal_shot"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, PortalShotPayload> STREAM_CODEC = StreamCodec.composite(
            GloveButton.STREAM_CODEC, PortalShotPayload::button,
            GooThrowPayload.STREAM_CODEC, PortalShotPayload::shot,
            PortalShotPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the payload on the server thread.
     *
     * @param payload the payload
     * @param context the network context
     */
    public static void handle(PortalShotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PortalShots.shoot(player, payload.button(), payload.shot());
            }
        });
    }
}

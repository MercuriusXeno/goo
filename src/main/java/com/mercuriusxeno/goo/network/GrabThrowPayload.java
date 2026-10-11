package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: a left click while Grab holds an entity throws
 * it along the player's look; the server throws only what that player holds.
 * grab-holds-and-throws-a-physics-body
 */
public record GrabThrowPayload() implements CustomPacketPayload {

    /** The one payload, since it carries nothing. */
    public static final GrabThrowPayload INSTANCE = new GrabThrowPayload();

    /** Payload type ID for registration. */
    public static final Type<GrabThrowPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "grab_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, GrabThrowPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Throws what the sending player holds, on the server thread.
     *
     * @param payload the throw
     * @param context the network context
     */
    public static void handle(GrabThrowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                GrabEvents.throwHeld(player);
            }
        });
    }
}

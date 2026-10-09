package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the caster's Scry sphere stands at a radius this
 * tick of the hold, sent to the caster alone, whose client draws the sphere
 * and reveals the air-exposed faces its front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param radius the sphere's radius in blocks
 */
public record ScryPayload(float radius) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ScryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "scry"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ScryPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeFloat(payload.radius), buf -> new ScryPayload(buf.readFloat()));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

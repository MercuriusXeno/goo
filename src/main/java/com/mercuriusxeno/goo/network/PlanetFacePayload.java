package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the blue planet's face, the overworld's biomes sampled around world spawn into a square of
 * colors, so the client draws each world's planet with its own continents and seas. Sent as a player joins.
 * decision planet-face-is-procgen-from-the-overworlds-biomes
 *
 * @param colors the face's colors, ARGB, row by row from the north
 */
public record PlanetFacePayload(int[] colors) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PlanetFacePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "planet_face"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, PlanetFacePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarIntArray(payload.colors()),
            buf -> new PlanetFacePayload(buf.readVarIntArray()));

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

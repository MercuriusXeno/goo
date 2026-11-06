package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-watchers payload: where Meteo's meteor struck and how far its
 * scorch reaches, for each watching client to lay its fading scorch over
 * the crater's faces; no block changes.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 *
 * @param center the point the meteor struck
 * @param radius how far from it the scorch reaches, in blocks
 */
public record MeteorScorchPayload(Vec3 center, float radius) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<MeteorScorchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "meteor_scorch"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, MeteorScorchPayload> STREAM_CODEC =
            StreamCodec.of(MeteorScorchPayload::encode, MeteorScorchPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, MeteorScorchPayload payload) {
        buf.writeDouble(payload.center.x);
        buf.writeDouble(payload.center.y);
        buf.writeDouble(payload.center.z);
        buf.writeFloat(payload.radius);
    }

    private static MeteorScorchPayload decode(FriendlyByteBuf buf) {
        return new MeteorScorchPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat());
    }
}

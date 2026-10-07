package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: one tick of a held ability, sent every tick the
 * glove's use stays down (decision stream-delivery-held-cone). A channel
 * reads the aim point and the plane its hold began at
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param origin    the glove hand, where the stream's cone opens from
 * @param aimPoint  the world point under the client's cursor
 * @param planeY    the player's feet height when the hold began
 */
public record GooStreamPayload(String gooTypeId, String abilityId, Vec3 origin, Vec3 aimPoint, double planeY)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooStreamPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_stream"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooStreamPayload> STREAM_CODEC =
            StreamCodec.of(GooStreamPayload::encode, GooStreamPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GooStreamPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        writePoint(buf, payload.origin);
        writePoint(buf, payload.aimPoint);
        buf.writeDouble(payload.planeY);
    }

    private static GooStreamPayload decode(FriendlyByteBuf buf) {
        return new GooStreamPayload(buf.readUtf(), buf.readUtf(), readPoint(buf), readPoint(buf), buf.readDouble());
    }

    private static void writePoint(FriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readPoint(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
}

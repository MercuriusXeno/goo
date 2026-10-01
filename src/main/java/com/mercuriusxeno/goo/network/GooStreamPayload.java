package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: one tick of a held stream ability, sent every
 * tick the glove's use stays down (decision stream-delivery-held-cone).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param origin    the glove hand, where the stream's cone opens from
 */
public record GooStreamPayload(String gooTypeId, String abilityId, Vec3 origin) implements CustomPacketPayload {

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
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
    }

    private static GooStreamPayload decode(FriendlyByteBuf buf) {
        return new GooStreamPayload(buf.readUtf(), buf.readUtf(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }
}

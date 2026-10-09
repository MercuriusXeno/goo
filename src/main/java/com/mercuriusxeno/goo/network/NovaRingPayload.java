package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a frost nova pulsed from a point, its ring
 * expanding to the reach the charge resolved, drawn by every client
 * watching (decision nova-ring-grows-with-the-hold).
 *
 * @param center the point the ring expands from, the caster's middle or a tap's landing
 * @param reach  the ring's reach in blocks
 */
public record NovaRingPayload(Vec3 center, float reach) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<NovaRingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "nova_ring"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, NovaRingPayload> STREAM_CODEC =
            StreamCodec.of(NovaRingPayload::encode, NovaRingPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, NovaRingPayload payload) {
        buf.writeDouble(payload.center.x);
        buf.writeDouble(payload.center.y);
        buf.writeDouble(payload.center.z);
        buf.writeFloat(payload.reach);
    }

    private static NovaRingPayload decode(FriendlyByteBuf buf) {
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new NovaRingPayload(center, buf.readFloat());
    }
}

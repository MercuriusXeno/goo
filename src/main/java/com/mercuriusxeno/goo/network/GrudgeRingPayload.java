package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a Grudge pulsed from its caster, its crimson ring
 * spreading to the reach the charge resolved, drawn by every client watching.
 * grudge-is-a-charged-crimson-nova-dealing-your-missing-health
 *
 * @param center the point the ring spreads from, the caster's middle
 * @param reach  the ring's reach in blocks
 */
public record GrudgeRingPayload(Vec3 center, float reach) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GrudgeRingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "grudge_ring"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GrudgeRingPayload> STREAM_CODEC =
            StreamCodec.of(GrudgeRingPayload::encode, GrudgeRingPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GrudgeRingPayload payload) {
        buf.writeDouble(payload.center.x);
        buf.writeDouble(payload.center.y);
        buf.writeDouble(payload.center.z);
        buf.writeFloat(payload.reach);
    }

    private static GrudgeRingPayload decode(FriendlyByteBuf buf) {
        Vec3 center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new GrudgeRingPayload(center, buf.readFloat());
    }
}

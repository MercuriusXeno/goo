package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the caster's Scry ping this tick of the hold,
 * sent to the caster alone, whose client draws the sphere from the ping's
 * origin and reveals the air-exposed faces its front crosses, fading the
 * sphere as it travels past its reach.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param origin where the ping began
 * @param radius the sphere's radius in blocks
 * @param reach  how far the front reaches before it fades
 * @param fade   how far past the reach it travels while fading
 */
public record ScryPayload(Vec3 origin, float radius, float reach, float fade) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ScryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "scry"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ScryPayload> STREAM_CODEC =
            StreamCodec.of(ScryPayload::encode, ScryPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ScryPayload payload) {
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
        buf.writeFloat(payload.radius);
        buf.writeFloat(payload.reach);
        buf.writeFloat(payload.fade);
    }

    private static ScryPayload decode(FriendlyByteBuf buf) {
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new ScryPayload(origin, buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}

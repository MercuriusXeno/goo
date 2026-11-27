package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a caster's Laser ray this tick of the hold,
 * from the caster to where it struck, sent to every client tracking the caster.
 *
 * @param casterId the casting player's entity id
 * @param end      where the ray stopped: the struck mob or block, or its reach
 * @param range    how far the ray reaches, which the caster's own client casts its drawn ray to
 *                 (decision diagnose-and-correct-the-sunbeam-jitter)
 */
public record LaserPayload(int casterId, Vec3 end, double range)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<LaserPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "laser"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, LaserPayload> STREAM_CODEC =
            StreamCodec.of(LaserPayload::encode, LaserPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, LaserPayload payload) {
        buf.writeVarInt(payload.casterId);
        writePoint(buf, payload.end);
        buf.writeDouble(payload.range);
    }

    private static LaserPayload decode(FriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        Vec3 end = readPoint(buf);
        return new LaserPayload(casterId, end, buf.readDouble());
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

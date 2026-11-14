package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: a caster's Laser ray this tick of the hold,
 * from the caster to where it struck, and on to each mob a prism it struck
 * refracted it toward, sent to every client tracking the caster.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 *
 * @param casterId  the casting player's entity id
 * @param end       where the ray stopped: the struck mob, block or prism, or its reach
 * @param refracted the points each refracted beam ends at, empty where no prism refracted it
 * @param range     how far the ray reaches, which the caster's own client casts its drawn ray to
 *                  (decision diagnose-and-correct-the-sunbeam-jitter)
 */
public record LaserPayload(int casterId, Vec3 end, List<Vec3> refracted, double range)
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
        buf.writeVarInt(payload.refracted.size());
        payload.refracted.forEach(point -> writePoint(buf, point));
        buf.writeDouble(payload.range);
    }

    private static LaserPayload decode(FriendlyByteBuf buf) {
        int casterId = buf.readVarInt();
        Vec3 end = readPoint(buf);
        int count = buf.readVarInt();
        List<Vec3> refracted = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            refracted.add(readPoint(buf));
        }
        return new LaserPayload(casterId, end, refracted, buf.readDouble());
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

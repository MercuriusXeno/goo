package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a mob-badged ability landed on a mob, thrown,
 * touched or punched, sent to the players tracking the struck mob so each
 * client plays the hit beside the program.
 * Decision hit-bursts-goo-particles.
 * Decision visuals-play-beside-the-program.
 *
 * @param entityId  the struck entity's id
 * @param gooTypeId the goo type's short id
 * @param hitPoint  the point on the mob the goo struck
 */
public record MobHitPayload(int entityId, String gooTypeId, Vec3 hitPoint) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<MobHitPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "mob_hit"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, MobHitPayload> STREAM_CODEC =
        StreamCodec.of(MobHitPayload::encode, MobHitPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Writes the payload to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, MobHitPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.gooTypeId);
        buf.writeDouble(payload.hitPoint.x);
        buf.writeDouble(payload.hitPoint.y);
        buf.writeDouble(payload.hitPoint.z);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static MobHitPayload decode(FriendlyByteBuf buf) {
        return new MobHitPayload(buf.readVarInt(), buf.readUtf(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }
}

package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Reap's leaf crescent slashes, flying out
 * from the slasher's chest along its heading. Sent to the players watching
 * the slasher's chunk.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param origin      where the slash launches
 * @param direction   its unit heading
 * @param reach       how far it flies, in blocks
 * @param flightTicks the game ticks it takes to fly its reach
 * @param delayTicks  the game ticks after the use it launches
 * @param roll        the blade's tilt about its heading, in degrees
 */
public record ReapSlashPayload(Vec3 origin, Vec3 direction, float reach, int flightTicks, int delayTicks,
                               float roll) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSlashPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_slash"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ReapSlashPayload> STREAM_CODEC =
        StreamCodec.of(ReapSlashPayload::encode, ReapSlashPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ReapSlashPayload payload) {
        Vec3.STREAM_CODEC.encode(buf, payload.origin);
        Vec3.STREAM_CODEC.encode(buf, payload.direction);
        buf.writeFloat(payload.reach);
        buf.writeVarInt(payload.flightTicks);
        buf.writeVarInt(payload.delayTicks);
        buf.writeFloat(payload.roll);
    }

    private static ReapSlashPayload decode(FriendlyByteBuf buf) {
        return new ReapSlashPayload(Vec3.STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }
}

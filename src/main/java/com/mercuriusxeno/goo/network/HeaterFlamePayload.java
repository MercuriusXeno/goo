package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload, sent each tick a heater prism flames a mob: where
 * its fire pours from and the way it points, so every watching client draws
 * Flames' fire from the prism and roars while the ticks keep coming
 * (decision heater-prism-flames-nearby-creatures).
 *
 * @param prism the heater prism, which keys its fire and its roar
 * @param tip   the tip of the prism's column, where the fire pours from
 * @param axis  the unit way the fire points, at the mob it flames
 */
public record HeaterFlamePayload(BlockPos prism, Vec3 tip, Vec3 axis) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<HeaterFlamePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "heater_flame"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, HeaterFlamePayload> STREAM_CODEC =
            StreamCodec.of(HeaterFlamePayload::encode, HeaterFlamePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, HeaterFlamePayload payload) {
        buf.writeBlockPos(payload.prism);
        buf.writeDouble(payload.tip.x);
        buf.writeDouble(payload.tip.y);
        buf.writeDouble(payload.tip.z);
        buf.writeFloat((float) payload.axis.x);
        buf.writeFloat((float) payload.axis.y);
        buf.writeFloat((float) payload.axis.z);
    }

    private static HeaterFlamePayload decode(FriendlyByteBuf buf) {
        BlockPos prism = buf.readBlockPos();
        Vec3 tip = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 axis = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new HeaterFlamePayload(prism, tip, axis);
    }
}

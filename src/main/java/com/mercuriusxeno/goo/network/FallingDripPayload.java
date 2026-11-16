package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a layer of world goo began falling through open
 * air, which the client draws as one falling drip from where the layer left
 * to the surface it lands on, arriving on the fall's last tick.
 * goo-moving-downward-falls-as-drips
 * falling-layer-lands-when-its-drip-arrives
 *
 * @param start    the point the layer leaves from
 * @param landingY the surface it lands on
 * @param ticks    the ticks it falls
 * @param dark     the goo's darker colour, RGB
 * @param light    the goo's lighter colour, RGB
 * @param paced    whether the drip moves down at a wall run's steady pace rather than falling free
 */
public record FallingDripPayload(Vec3 start, double landingY, int ticks, int dark, int light, boolean paced)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<FallingDripPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "falling_drip"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, FallingDripPayload> STREAM_CODEC =
            StreamCodec.of(FallingDripPayload::encode, FallingDripPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, FallingDripPayload payload) {
        buf.writeDouble(payload.start.x);
        buf.writeDouble(payload.start.y);
        buf.writeDouble(payload.start.z);
        buf.writeDouble(payload.landingY);
        buf.writeVarInt(payload.ticks);
        buf.writeInt(payload.dark);
        buf.writeInt(payload.light);
        buf.writeBoolean(payload.paced);
    }

    private static FallingDripPayload decode(FriendlyByteBuf buf) {
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new FallingDripPayload(start, buf.readDouble(), buf.readVarInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean());
    }
}

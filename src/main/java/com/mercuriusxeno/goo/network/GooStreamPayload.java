package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Client-to-server payload: one tick of a held ability, sent every tick the
 * glove's use stays down (decision stream-delivery-held-cone). A channel
 * reads the aim point and the face its hold began on
 * (decision flatten-disc-cursor-breaks-above-the-plane).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param origin    the glove hand, where the stream's cone opens from
 * @param aimPoint  the world point under the client's cursor
 * @param planeBlock the block the cursor rested on when the hold began
 * @param planeFace  the side of it the cursor rested on, by its 3D data value, or NO_FACE where it rested on none
 */
public record GooStreamPayload(String gooTypeId, String abilityId, Vec3 origin, Vec3 aimPoint, BlockPos planeBlock,
                               int planeFace) implements CustomPacketPayload {

    /** The face a hold that began on no block carries. */
    public static final int NO_FACE = -1;

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
        writePoint(buf, payload.origin);
        writePoint(buf, payload.aimPoint);
        buf.writeBlockPos(payload.planeBlock);
        buf.writeVarInt(payload.planeFace);
    }

    private static GooStreamPayload decode(FriendlyByteBuf buf) {
        return new GooStreamPayload(buf.readUtf(), buf.readUtf(), readPoint(buf), readPoint(buf), buf.readBlockPos(),
                buf.readVarInt());
    }

    /**
     * One tick of a held ability whose hold began on no face, as a stream's is.
     *
     * @param gooTypeId the goo type string identifier
     * @param abilityId the selected ability id string
     * @param origin    the glove hand
     * @param aimPoint  the world point under the cursor
     * @return the payload
     */
    public static GooStreamPayload unplaned(String gooTypeId, String abilityId, Vec3 origin, Vec3 aimPoint) {
        return new GooStreamPayload(gooTypeId, abilityId, origin, aimPoint, BlockPos.ZERO, NO_FACE);
    }

    /**
     * The face the hold began on.
     *
     * @return the face, or null where the hold began on none
     */
    public ChannelAim.@Nullable FacePlane plane() {
        return planeFace == NO_FACE ? null : new ChannelAim.FacePlane(planeBlock, Direction.from3DDataValue(planeFace));
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

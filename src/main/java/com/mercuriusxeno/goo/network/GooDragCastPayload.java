package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the release of a world ability sized at will,
 * naming the epicenter right click pinned and the radius the drag set
 * (decision black-hole-leaves-a-compression-sphere).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param pinBlock  the block the cursor rested on at the press
 * @param pinFace   the side of it the cursor rested on, by its 3D data value
 * @param pinPoint  the point on that face the press pinned
 * @param radius    the radius the drag set, in blocks
 */
public record GooDragCastPayload(String gooTypeId, String abilityId, BlockPos pinBlock, int pinFace, Vec3 pinPoint,
                                 double radius) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooDragCastPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_drag_cast"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooDragCastPayload> STREAM_CODEC =
            StreamCodec.of(GooDragCastPayload::encode, GooDragCastPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * @return the face the press pinned
     */
    public Direction face() {
        return Direction.from3DDataValue(pinFace);
    }

    private static void encode(FriendlyByteBuf buf, GooDragCastPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeBlockPos(payload.pinBlock);
        buf.writeVarInt(payload.pinFace);
        buf.writeDouble(payload.pinPoint.x);
        buf.writeDouble(payload.pinPoint.y);
        buf.writeDouble(payload.pinPoint.z);
        buf.writeDouble(payload.radius);
    }

    private static GooDragCastPayload decode(FriendlyByteBuf buf) {
        return new GooDragCastPayload(buf.readUtf(), buf.readUtf(), buf.readBlockPos(), buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readDouble());
    }
}

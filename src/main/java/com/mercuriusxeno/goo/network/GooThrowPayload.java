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
 * Client-to-server payload: requests throwing the selected goo type at a target.
 * The server validates goo availability and range before executing the throw.
 *
 * @param gooTypeId      the goo type string identifier
 * @param targetEntityId the target entity ID, or -1 for block targets
 * @param targetPos      the target block position
 * @param targetFace     the target face ordinal
 * @param grannyArc      whether the throw is a lob onto a top face
 * @param abilityId      the selected ability id string
 * @param origin         the aim line's start at the click, the point the flight leaves from
 */
public record GooThrowPayload(String gooTypeId, int targetEntityId,
                               BlockPos targetPos, int targetFace,
                               boolean grannyArc, String abilityId, Vec3 origin)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooThrowPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_throw"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooThrowPayload> STREAM_CODEC =
        StreamCodec.of(GooThrowPayload::encode, GooThrowPayload::decode);

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
    private static void encode(FriendlyByteBuf buf, GooThrowPayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFace);
        buf.writeBoolean(payload.grannyArc);
        buf.writeUtf(payload.abilityId);
        buf.writeDouble(payload.origin.x);
        buf.writeDouble(payload.origin.y);
        buf.writeDouble(payload.origin.z);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static GooThrowPayload decode(FriendlyByteBuf buf) {
        String gooTypeId = buf.readUtf();
        int targetEntityId = buf.readVarInt();
        BlockPos targetPos = buf.readBlockPos();
        int targetFace = buf.readVarInt();
        boolean grannyArc = buf.readBoolean();
        String abilityId = buf.readUtf();
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new GooThrowPayload(gooTypeId, targetEntityId, targetPos, targetFace,
                grannyArc, abilityId, origin);
    }
}

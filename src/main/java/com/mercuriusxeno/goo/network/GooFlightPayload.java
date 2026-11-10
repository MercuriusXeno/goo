package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Server-to-client payload: broadcasts a goo in flight so nearby clients
 * can render the projectile arc. Sent to all players tracking the thrower.
 *
 * @param startX         the starting X position
 * @param startY         the starting Y position
 * @param startZ         the starting Z position
 * @param gooTypeId      the goo type string identifier
 * @param targetEntityId the target entity ID, or -1 for block targets
 * @param targetPos      the target block position
 * @param targetFace     the target face ordinal
 * @param travelTicks    the number of ticks for the flight arc
 * @param grannyArc      whether the throw is a lob onto a top face
 * @param abilityId      the ability id string the goo carries
 * @param delivery       the ability's delivery, so the client renders the flight without a registry
 *                       (decision delivery-block-in-ability-json)
 * @param targetPoint    the exact point a block flight lands at (decision aim-point-follows-the-cursor)
 * @param gate           the linked gate the flight passes to reach a far-side target, empty on the near side
 *                       (decision aim-and-shots-carry-through-linked-gates)
 * @param facePoint      where the flight meets that gate's face, unread with no gate
 */
public record GooFlightPayload(double startX, double startY, double startZ,
                                String gooTypeId, int targetEntityId,
                                BlockPos targetPos, int targetFace,
                                int travelTicks, boolean grannyArc,
                                String abilityId, Delivery delivery, Vec3 targetPoint,
                                Optional<Gate> gate, Vec3 facePoint)
        implements CustomPacketPayload {

    /**
     * A flight to a near-side target, passing no gate.
     *
     * @param startX         the starting X position
     * @param startY         the starting Y position
     * @param startZ         the starting Z position
     * @param gooTypeId      the goo type string identifier
     * @param targetEntityId the target entity ID, or -1 for block targets
     * @param targetPos      the target block position
     * @param targetFace     the target face ordinal
     * @param travelTicks    the number of ticks for the flight arc
     * @param grannyArc      whether the throw is a lob onto a top face
     * @param abilityId      the ability id string the goo carries
     * @param delivery       the ability's delivery
     * @param targetPoint    the exact point a block flight lands at
     */
    public GooFlightPayload(double startX, double startY, double startZ, String gooTypeId, int targetEntityId,
            BlockPos targetPos, int targetFace, int travelTicks, boolean grannyArc, String abilityId,
            Delivery delivery, Vec3 targetPoint) {
        this(startX, startY, startZ, gooTypeId, targetEntityId, targetPos, targetFace, travelTicks, grannyArc,
                abilityId, delivery, targetPoint, Optional.empty(), Vec3.ZERO);
    }

    /** Payload type ID for registration. */
    public static final Type<GooFlightPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_flight"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooFlightPayload> STREAM_CODEC =
        StreamCodec.of(GooFlightPayload::encode, GooFlightPayload::decode);

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
    private static void encode(FriendlyByteBuf buf, GooFlightPayload payload) {
        encodeStart(buf, payload);
        encodeTarget(buf, payload);
    }

    /** Writes start position and goo type.
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeStart(FriendlyByteBuf buf, GooFlightPayload payload) {
        buf.writeDouble(payload.startX);
        buf.writeDouble(payload.startY);
        buf.writeDouble(payload.startZ);
        buf.writeUtf(payload.gooTypeId);
    }

    /** Writes target entity, position, face, travel time, arc flag, ability and delivery.
     *
     * @param buf     the output buffer
     * @param payload the payload
     */
    private static void encodeTarget(FriendlyByteBuf buf, GooFlightPayload payload) {
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFace);
        buf.writeVarInt(payload.travelTicks);
        buf.writeBoolean(payload.grannyArc);
        buf.writeUtf(payload.abilityId);
        Delivery.STREAM_CODEC.encode(buf, payload.delivery);
        buf.writeDouble(payload.targetPoint.x);
        buf.writeDouble(payload.targetPoint.y);
        buf.writeDouble(payload.targetPoint.z);
        buf.writeBoolean(payload.gate.isPresent());
        payload.gate.ifPresent(passed -> Gate.STREAM_CODEC.encode(buf, passed));
        Vec3.STREAM_CODEC.encode(buf, payload.facePoint);
    }

    /**
     * Reads the payload from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static GooFlightPayload decode(FriendlyByteBuf buf) {
        return new GooFlightPayload(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readUtf(), buf.readVarInt(), buf.readBlockPos(),
                buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(), Delivery.STREAM_CODEC.decode(buf),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readBoolean() ? Optional.of(Gate.STREAM_CODEC.decode(buf)) : Optional.empty(),
                Vec3.STREAM_CODEC.decode(buf));
    }
}

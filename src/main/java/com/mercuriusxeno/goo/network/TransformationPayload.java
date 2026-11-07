package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Server-to-client payload: a goo blob hops from a point to an entity or a
 * block and transforms into it, the blob shrinking as the target's model
 * grows from nothing to full size.
 * Decision model-transformation-is-one-animation.
 * Decision prism-blob-becomes-a-milky-quartz-crystal.
 *
 * @param gooType        the goo type of the blob
 * @param from           the world point the blob leaves from
 * @param to             the world point the target stands at
 * @param targetEntityId the entity the blob becomes, {@link #NO_ENTITY} for a block target
 * @param targetBlock    the block the blob becomes, null for an entity target
 * @param ticks          the game ticks the whole transformation takes
 * @param standIn        the type of the entity the server holds back until the transformation ends, which the
 *                       client stands in for under the entity's id meanwhile; null where the entity already stands
 */
public record TransformationPayload(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
        @Nullable BlockPos targetBlock, int ticks, @Nullable Identifier standIn) implements CustomPacketPayload {

    /** The entity id a block-targeted transformation names. */
    public static final int NO_ENTITY = -1;

    /** Payload type ID for registration. */
    public static final Type<TransformationPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "transformation"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, TransformationPayload> STREAM_CODEC =
        StreamCodec.of(TransformationPayload::encode, TransformationPayload::decode);

    /**
     * A transformation into an entity.
     *
     * @param gooType        the goo type of the blob
     * @param from           the world point the blob leaves from
     * @param to             the world point the entity stands at
     * @param targetEntityId the entity the blob becomes
     * @param ticks          the game ticks the whole transformation takes
     */
    public TransformationPayload(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
            int ticks) {
        this(gooType, from, to, targetEntityId, null, ticks, null);
    }

    /**
     * A transformation into an entity the server holds back until it ends:
     * the client morphs the blob into a stand-in of the entity's type under
     * the entity's id, which the entity's own spawn replaces
     * (decision slime-blob-morphs-into-the-slime-it-becomes).
     *
     * @param gooType        the goo type of the blob
     * @param from           the world point the blob leaves from
     * @param to             the world point the entity will stand at
     * @param targetEntityId the id the entity holds from construction
     * @param standIn        the entity's type
     * @param ticks          the game ticks the whole transformation takes
     * @return the payload
     */
    public static TransformationPayload intoHeldEntity(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to,
            int targetEntityId, Identifier standIn, int ticks) {
        return new TransformationPayload(gooType, from, to, targetEntityId, null, ticks, standIn);
    }

    /**
     * A transformation into the block at a position.
     *
     * @param gooType     the goo type of the blob
     * @param from        the world point the blob leaves from
     * @param to          the world point the block's model stands at
     * @param targetBlock the block the blob becomes
     * @param ticks       the game ticks the whole transformation takes
     * @return the payload
     */
    public static TransformationPayload intoBlock(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to,
            BlockPos targetBlock, int ticks) {
        return new TransformationPayload(gooType, from, to, NO_ENTITY, targetBlock, ticks, null);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, TransformationPayload payload) {
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        Vec3.STREAM_CODEC.encode(buf, payload.from);
        Vec3.STREAM_CODEC.encode(buf, payload.to);
        buf.writeVarInt(payload.targetEntityId);
        buf.writeBoolean(payload.targetBlock != null);
        if (payload.targetBlock != null) {
            buf.writeBlockPos(payload.targetBlock);
        }
        buf.writeVarInt(payload.ticks);
        buf.writeNullable(payload.standIn, Identifier.STREAM_CODEC);
    }

    private static TransformationPayload decode(FriendlyByteBuf buf) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.KEY_STREAM_CODEC.decode(buf);
        Vec3 from = Vec3.STREAM_CODEC.decode(buf);
        Vec3 to = Vec3.STREAM_CODEC.decode(buf);
        int targetEntityId = buf.readVarInt();
        BlockPos targetBlock = buf.readBoolean() ? buf.readBlockPos() : null;
        int ticks = buf.readVarInt();
        return new TransformationPayload(gooType, from, to, targetEntityId, targetBlock, ticks,
                buf.readNullable(Identifier.STREAM_CODEC));
    }
}

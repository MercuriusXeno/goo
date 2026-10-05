package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a goo blob hops from a point to an entity and
 * transforms into it, the blob shrinking as the entity's model grows from
 * nothing to full size.
 * Decision model-transformation-is-one-animation.
 *
 * @param gooType        the goo type of the blob
 * @param from           the world point the blob leaves from
 * @param to             the world point the entity stands at
 * @param targetEntityId the entity the blob becomes
 * @param ticks          the game ticks the whole transformation takes
 */
public record TransformationPayload(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
        int ticks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<TransformationPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "transformation"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, TransformationPayload> STREAM_CODEC =
        StreamCodec.of(TransformationPayload::encode, TransformationPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, TransformationPayload payload) {
        GooTypes.KEY_STREAM_CODEC.encode(buf, payload.gooType);
        Vec3.STREAM_CODEC.encode(buf, payload.from);
        Vec3.STREAM_CODEC.encode(buf, payload.to);
        buf.writeVarInt(payload.targetEntityId);
        buf.writeVarInt(payload.ticks);
    }

    private static TransformationPayload decode(FriendlyByteBuf buf) {
        return new TransformationPayload(GooTypes.KEY_STREAM_CODEC.decode(buf), Vec3.STREAM_CODEC.decode(buf),
                Vec3.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt());
    }
}

package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: an entity's model shrinks smoothly from one
 * size to another, as Rewind turns an adult into a baby or a mob into its
 * egg.
 * Decision model-transformation-is-one-animation.
 * rewind-shrinks-adult-to-baby-to-egg
 *
 * @param entityId  the shrinking entity's id
 * @param fromScale the model's size as the shrink begins, as a multiple of its drawn size
 * @param toScale   the model's size as the shrink ends
 * @param babyModel true when the shrink draws on the baby model, so it holds off until the client sees a baby
 * @param ticks     the game ticks the shrink takes
 */
public record ModelShrinkPayload(int entityId, float fromScale, float toScale, boolean babyModel, int ticks)
        implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ModelShrinkPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "model_shrink"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, ModelShrinkPayload> STREAM_CODEC =
        StreamCodec.of(ModelShrinkPayload::encode, ModelShrinkPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, ModelShrinkPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeFloat(payload.fromScale);
        buf.writeFloat(payload.toScale);
        buf.writeBoolean(payload.babyModel);
        buf.writeVarInt(payload.ticks);
    }

    private static ModelShrinkPayload decode(FriendlyByteBuf buf) {
        return new ModelShrinkPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readVarInt());
    }
}

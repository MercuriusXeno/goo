package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: an entity wears a status ailment for a span of
 * ticks, sent to the players tracking it and to the entity itself, so every
 * client draws the ailment's overlay on its model.
 * Decision ailment-overlay-shader-per-ailment.
 *
 * @param entityId      the afflicted entity's id
 * @param kind          the ailment
 * @param durationTicks how long it lasts, in game ticks
 */
public record AilmentPayload(int entityId, AilmentKind kind, int durationTicks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<AilmentPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ailment"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, AilmentPayload> STREAM_CODEC =
        StreamCodec.of(AilmentPayload::encode, AilmentPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, AilmentPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeEnum(payload.kind);
        buf.writeVarInt(payload.durationTicks);
    }

    private static AilmentPayload decode(FriendlyByteBuf buf) {
        return new AilmentPayload(buf.readVarInt(), buf.readEnum(AilmentKind.class), buf.readVarInt());
    }
}

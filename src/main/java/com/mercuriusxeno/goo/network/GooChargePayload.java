package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: a charged ability let go, carrying how many
 * ticks the glove's use was held, which the server reads as the share of a
 * full charge (decision nova-ring-grows-with-the-hold).
 *
 * @param gooTypeId the goo type string identifier
 * @param abilityId the selected ability id string
 * @param heldTicks the ticks the use key was held before release
 */
public record GooChargePayload(String gooTypeId, String abilityId, int heldTicks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooChargePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_charge"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooChargePayload> STREAM_CODEC =
            StreamCodec.of(GooChargePayload::encode, GooChargePayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GooChargePayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeVarInt(payload.heldTicks);
    }

    private static GooChargePayload decode(FriendlyByteBuf buf) {
        return new GooChargePayload(buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }
}

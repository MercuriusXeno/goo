package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.Optional;

/**
 * Client-to-server payload: a charged ability let go, carrying how many
 * ticks the glove's use was held, which the server reads as the share of a
 * full charge (decision nova-ring-grows-with-the-hold), the press's
 * scatter seed, which a slinging release throws its knives by so they fly
 * along the lines the client drew (decision knives-aims-a-spread-fan-not-a-blob-line),
 * and, for a spontaneous cast, the target the crosshair aimed as it let go
 * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
 *
 * @param gooTypeId   the goo type string identifier
 * @param abilityId   the selected ability id string
 * @param heldTicks   the ticks the use key was held before release
 * @param scatterSeed the press's scatter seed
 * @param aimed       the target a spontaneous cast lands at, empty for a charge aiming none
 */
public record GooChargePayload(String gooTypeId, String abilityId, int heldTicks, long scatterSeed,
                               Optional<GooThrowPayload> aimed) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<GooChargePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_charge"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, GooChargePayload> STREAM_CODEC =
            StreamCodec.of(GooChargePayload::encode, GooChargePayload::decode);

    /**
     * A charge aiming no target: a self ability's or a slinging throw's.
     *
     * @param gooTypeId   the goo type string identifier
     * @param abilityId   the selected ability id string
     * @param heldTicks   the ticks the use key was held before release
     * @param scatterSeed the press's scatter seed
     */
    public GooChargePayload(String gooTypeId, String abilityId, int heldTicks, long scatterSeed) {
        this(gooTypeId, abilityId, heldTicks, scatterSeed, Optional.empty());
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, GooChargePayload payload) {
        buf.writeUtf(payload.gooTypeId);
        buf.writeUtf(payload.abilityId);
        buf.writeVarInt(payload.heldTicks);
        buf.writeLong(payload.scatterSeed);
        buf.writeBoolean(payload.aimed.isPresent());
        payload.aimed.ifPresent(target -> GooThrowPayload.STREAM_CODEC.encode(buf, target));
    }

    private static GooChargePayload decode(FriendlyByteBuf buf) {
        String gooTypeId = buf.readUtf();
        String abilityId = buf.readUtf();
        int heldTicks = buf.readVarInt();
        long scatterSeed = buf.readLong();
        Optional<GooThrowPayload> aimed = buf.readBoolean()
                ? Optional.of(GooThrowPayload.STREAM_CODEC.decode(buf)) : Optional.empty();
        return new GooChargePayload(gooTypeId, abilityId, heldTicks, scatterSeed, aimed);
    }
}

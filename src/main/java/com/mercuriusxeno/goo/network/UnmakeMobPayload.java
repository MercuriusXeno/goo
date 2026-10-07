package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: an unmake has melted this share of a mob, so the
 * client draws its body liquefying into goo.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param entityId the mob's entity id
 * @param fraction the share melted, from 0 whole to 1 gone
 */
public record UnmakeMobPayload(int entityId, float fraction) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<UnmakeMobPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "unmake_mob"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, UnmakeMobPayload> STREAM_CODEC =
        StreamCodec.of(UnmakeMobPayload::encode, UnmakeMobPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(FriendlyByteBuf buf, UnmakeMobPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeFloat(payload.fraction);
    }

    private static UnmakeMobPayload decode(FriendlyByteBuf buf) {
        return new UnmakeMobPayload(buf.readVarInt(), buf.readFloat());
    }
}

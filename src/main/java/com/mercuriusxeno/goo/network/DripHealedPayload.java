package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a vital tap's drip healed this living thing, so
 * the client raises the channel's pink healing stars on it. A tap has no
 * glove, so no goo homes. Sent to the players watching the healed thing and
 * to it when it is a player.
 * vitality-drip-heals-below
 *
 * @param healedId the living thing the drip healed
 */
public record DripHealedPayload(int healedId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<DripHealedPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "drip_healed"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, DripHealedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DripHealedPayload::healedId,
            DripHealedPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

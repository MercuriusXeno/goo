package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: a caster holds Radiant this tick, sent to every
 * client tracking the caster, which drifts glow motes off the caster's glove
 * (operator rulings 2026-10-09).
 * decision radiant-wisps-where-light-is-low
 *
 * @param casterId the casting player's entity id
 */
public record RadiantAuraPayload(int casterId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<RadiantAuraPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "radiant_aura"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, RadiantAuraPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(RadiantAuraPayload::new, RadiantAuraPayload::casterId).cast();

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

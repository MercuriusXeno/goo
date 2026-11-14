package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: the slime shell a charm-like cast's dart left on
 * a mob bursts off it, as a failed attempt does, sent to the players
 * tracking the mob.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 *
 * @param entityId the mob's id
 */
public record EnvelopBurstPayload(int entityId) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<EnvelopBurstPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "envelop_burst"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, EnvelopBurstPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EnvelopBurstPayload::new, EnvelopBurstPayload::entityId);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

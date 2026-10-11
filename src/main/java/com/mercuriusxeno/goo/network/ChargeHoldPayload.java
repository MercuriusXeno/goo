package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Client-to-server payload: the player began or stopped holding a
 * slinging charge, so the players watching draw the glove arm wound up
 * (decision shards-sling-then-morph-to-flechettes).
 *
 * @param holding true as the hold begins, false as it ends
 */
public record ChargeHoldPayload(boolean holding) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ChargeHoldPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "charge_hold"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ChargeHoldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ChargeHoldPayload::holding,
            ChargeHoldPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

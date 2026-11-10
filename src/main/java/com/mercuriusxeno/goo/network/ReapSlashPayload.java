package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapBlade;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one of Reap's leaf crescent blades, flying out
 * along the slasher's look a few ticks after it arrives, as its place in its
 * swing names. Sent to the players watching the slasher's chunk; the client
 * draws it from the same blade the server reaps and cuts with.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param blade      the blade
 * @param delayTicks the ticks after the payload arrives that the blade flies
 */
public record ReapSlashPayload(ReapBlade blade, int delayTicks) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<ReapSlashPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "reap_slash"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, ReapSlashPayload> STREAM_CODEC = StreamCodec.composite(
        ReapBlade.STREAM_CODEC, ReapSlashPayload::blade,
        ByteBufCodecs.VAR_INT, ReapSlashPayload::delayTicks,
        ReapSlashPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

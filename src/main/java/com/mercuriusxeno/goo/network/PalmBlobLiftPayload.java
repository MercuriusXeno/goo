package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Server-to-client payload: one tick a caster holds a cast, a stream or a
 * channel the server ran or a charge the caster's client held, so every
 * client drawing that caster keeps the caster's palm blob lifted while the
 * ticks keep coming; or the tick a durative use landed, which spends the
 * lifted blob, so it swells and vanishes.
 * every-ability-casts-from-the-lifted-palm-blob
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 *
 * @param casterId the caster's entity id
 * @param glove    where the server holds the caster's glove this tick
 * @param spent    whether a durative use landed, spending the blob, rather than a cast held
 */
public record PalmBlobLiftPayload(int casterId, Vec3 glove, boolean spent) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PalmBlobLiftPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "palm_blob_lift"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PalmBlobLiftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PalmBlobLiftPayload::casterId,
            Vec3.STREAM_CODEC, PalmBlobLiftPayload::glove,
            ByteBufCodecs.BOOL, PalmBlobLiftPayload::spent,
            PalmBlobLiftPayload::new);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

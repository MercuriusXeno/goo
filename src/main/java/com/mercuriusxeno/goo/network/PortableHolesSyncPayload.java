package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.List;

/**
 * Server-to-client payload: the Void portable holes standing open in the
 * player's level, each as the face its mouth opens on, so the client draws
 * each mouth. Sent whenever a hole opens or closes and as a player joins or
 * changes level.
 * decision hole-is-an-in-world-portable-hole-for-items
 *
 * @param mouths each open hole's mouth
 */
public record PortableHolesSyncPayload(List<Mouth> mouths) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<PortableHolesSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portable_holes_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortableHolesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Mouth.STREAM_CODEC.apply(ByteBufCodecs.list()), PortableHolesSyncPayload::mouths,
            PortableHolesSyncPayload::new);

    /**
     * One hole's mouth.
     *
     * @param block    the block whose face it opens on
     * @param face     the face it opens on
     * @param closesAt the game time it closes
     */
    public record Mouth(BlockPos block, Direction face, long closesAt) {

        /** Sends a mouth. */
        public static final StreamCodec<ByteBuf, Mouth> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Mouth::block,
                Direction.STREAM_CODEC, Mouth::face,
                ByteBufCodecs.VAR_LONG, Mouth::closesAt,
                Mouth::new);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

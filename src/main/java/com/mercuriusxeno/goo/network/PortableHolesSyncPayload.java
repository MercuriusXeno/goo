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
 * player's level, each as the faces its mouth opens across and the kinds its
 * owner stored, so the client draws each mouth with those kinds swimming in
 * it. Sent whenever a hole opens, closes or swallows, and as a player joins
 * or changes level.
 * decision hole-is-an-in-world-portable-hole-for-items
 *
 * @param mouths each open hole's mouth
 */
public record PortableHolesSyncPayload(List<Mouth> mouths) implements CustomPacketPayload {

    /** The most stored kinds one mouth carries, the most its client draws swimming. */
    public static final int MOST_STORED_SHOWN = 12;

    /** Payload type ID for registration. */
    public static final Type<PortableHolesSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "portable_holes_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<ByteBuf, PortableHolesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            Mouth.STREAM_CODEC.apply(ByteBufCodecs.list()), PortableHolesSyncPayload::mouths,
            PortableHolesSyncPayload::new);

    /** The most blocks one mouth covers, a 3x3. */
    public static final int MOST_FOOTPRINT_CELLS = 9;

    /**
     * One hole's mouth.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param footprint the blocks whose faces it opens across
     * @param face      the face it opens on
     * @param closesAt  the game time it closes
     * @param stored    the first kinds its owner stored, in shelf order
     */
    public record Mouth(List<BlockPos> footprint, Direction face, long closesAt, List<Stored> stored) {

        /** Sends a mouth. */
        public static final StreamCodec<ByteBuf, Mouth> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_FOOTPRINT_CELLS)), Mouth::footprint,
                Direction.STREAM_CODEC, Mouth::face,
                ByteBufCodecs.VAR_LONG, Mouth::closesAt,
                Stored.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_STORED_SHOWN)), Mouth::stored,
                Mouth::new);
    }

    /**
     * One kind the hole's owner has stored, which the client draws swimming
     * in the mouth.
     *
     * @param item  the item's id
     * @param count how many are stored
     */
    public record Stored(Identifier item, long count) {

        /** Sends a stored kind. */
        public static final StreamCodec<ByteBuf, Stored> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Stored::item,
                ByteBufCodecs.VAR_LONG, Stored::count,
                Stored::new);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
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
     * @param openedAt  the game time it opened, which tells one hole from the next opened in its place
     * @param closesAt  the game time it closes
     * @param stored    the first kinds its owner stored, in shelf order
     * @param swallowed the items it swallowed last, as many as it shows stored
     */
    public record Mouth(List<BlockPos> footprint, Direction face, long openedAt, long closesAt,
                        List<Stored> stored, List<Swallowed> swallowed) {

        /** Sends a mouth. */
        public static final StreamCodec<ByteBuf, Mouth> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_FOOTPRINT_CELLS)), Mouth::footprint,
                Direction.STREAM_CODEC, Mouth::face,
                ByteBufCodecs.VAR_LONG, Mouth::openedAt,
                ByteBufCodecs.VAR_LONG, Mouth::closesAt,
                Stored.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_STORED_SHOWN)), Mouth::stored,
                Swallowed.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_STORED_SHOWN)), Mouth::swallowed,
                Mouth::new);
    }

    /**
     * One item a hole swallowed, which the client draws easing from where it
     * stood into the soup.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     *
     * @param item the swallowed item's id
     * @param at   the place the item stood as it was swallowed
     * @param time the game time it was swallowed
     */
    public record Swallowed(Identifier item, Vec3 at, long time) {

        /** Sends a swallowed item. */
        public static final StreamCodec<ByteBuf, Swallowed> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Swallowed::item,
                Vec3.STREAM_CODEC, Swallowed::at,
                ByteBufCodecs.VAR_LONG, Swallowed::time,
                Swallowed::new);
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

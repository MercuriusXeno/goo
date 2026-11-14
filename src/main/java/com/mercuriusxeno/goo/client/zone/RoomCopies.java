package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the copies of a Zone room stand around it: the room repeats
 * every whole width of itself east-west and north-south, corners included,
 * out to the client's render distance in blocks, so a small room shows more
 * copies along a side than a large one. The width is twice the room's
 * extent, a fraction of a block where the extent ends partway through one.
 * decision room-tiles-to-render-distance-held-to-the-picture
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 */
public final class RoomCopies {

    private static final int BLOCKS_PER_CHUNK = 16;
    /** From a room's extent, the distance its boundary stands from the middle, to its width. */
    private static final int SIDES = 2;

    private RoomCopies() {
    }

    /**
     * How far one copy stands from the room itself.
     *
     * @param east  blocks east, negative for west
     * @param south blocks south, negative for north
     */
    public record Offset(double east, double south) {
    }

    /**
     * The reach the copies tile out to: the client's render distance counted in blocks.
     *
     * @param renderDistanceChunks the client's effective render distance, in chunks
     * @return the reach in blocks
     */
    public static double reachOf(int renderDistanceChunks) {
        return (double) renderDistanceChunks * BLOCKS_PER_CHUNK;
    }

    /**
     * Every copy of a room standing within a reach of its edge: each whole
     * multiple of its width east-west and north-south whose near edge stands
     * no farther than the reach past the room's own, the corners among them,
     * the room's own place left out. The copies touching the room always
     * stand, so a room wider than the reach still draws to the render distance.
     *
     * @param bounds the room's boundary
     * @param reach  how far the copies tile past the room's edge, in blocks
     * @return the copies' offsets, west to east within each row, rows north to south
     */
    public static List<Offset> offsets(RoomExtent.Bounds bounds, double reach) {
        double width = SIDES * bounds.extent();
        if (width <= 0) {
            return List.of();
        }
        int along = (int) Math.floor(reach / width) + 1;
        List<Offset> offsets = new ArrayList<>();
        for (int south = -along; south <= along; south++) {
            for (int east = -along; east <= along; east++) {
                if (east != 0 || south != 0) {
                    offsets.add(new Offset(east * width, south * width));
                }
            }
        }
        return offsets;
    }
}

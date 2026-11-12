package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import java.util.ArrayList;
import java.util.List;

/**
 * Which voidstone draws its stars: a block lying wholly inside the room's
 * boundary, and the block the boundary cuts, the one a build refuses, both
 * draw the same tiling starfield, which the shader clips at the boundary
 * plane. The test reads the boundary the client holds now, so a block gains
 * its stars on the edge update that reaches it, with no section rebuilt. A
 * block wholly beyond the boundary keeps its plain dark faces, under the
 * room's copies drawing their own stars over it, and a player standing in no
 * known room sees every voidstone starred.
 * decision boundary-cut-voidstone-draws-without-its-stars
 * decision room-edge-draws-a-sparser-starfield
 */
public final class VoidstoneStars {

    private VoidstoneStars() {
    }

    /**
     * One floor column a room lays starred faces on.
     *
     * @param x the column's x
     * @param z the column's z
     */
    public record Column(int x, int z) {
    }

    /**
     * Whether a voidstone draws its stars, by the room the client stands in now.
     *
     * @param x the block's x
     * @param z the block's z
     * @return true where its column stands inside the boundary or the boundary cuts it, or no room is known
     */
    public static boolean starred(int x, int z) {
        return ClientRoomEdges.bounds().map(bounds -> starredIn(bounds, x, z)).orElse(true);
    }

    /**
     * Whether a voidstone draws its stars in a room.
     *
     * @param bounds the room's boundary
     * @param x      the block's x
     * @param z      the block's z
     * @return true where its column stands wholly inside the boundary or the boundary cuts it
     */
    static boolean starredIn(RoomExtent.Bounds bounds, int x, int z) {
        return bounds.holdsWholeBlock(x, z) || bounds.cutsBlock(x, z);
    }

    /**
     * The floor columns a room lays starred faces on: the whole square, and
     * the ring around it the boundary cuts where it cuts one.
     *
     * @param bounds the room's boundary
     * @return the columns, west to east within each row, rows north to south
     */
    static List<Column> floorColumns(RoomExtent.Bounds bounds) {
        int reach = RoomExtent.wholeRings(bounds.extent()) + 1;
        List<Column> columns = new ArrayList<>();
        for (int z = bounds.centerZ() - reach; z <= bounds.centerZ() + reach; z++) {
            for (int x = bounds.centerX() - reach; x <= bounds.centerX() + reach; x++) {
                if (starredIn(bounds, x, z)) {
                    columns.add(new Column(x, z));
                }
            }
        }
        return columns;
    }
}

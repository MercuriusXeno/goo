package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import java.util.ArrayList;
import java.util.List;

/**
 * How densely a voidstone draws its stars: a block lying wholly inside the
 * room's boundary draws the full starfield, and the block the boundary cuts,
 * the one a build refuses, a sparser one, so the edge of what may be built on
 * shows as thinning stars rather than solid black. The test reads the
 * boundary the client holds now, so a block gains its full stars on the edge
 * update that clears its far face, with no section rebuilt. A block wholly
 * beyond the boundary keeps its plain dark faces, under the room's copies
 * drawing their own stars over it, and a player standing in no known room
 * sees every voidstone fully starred.
 * decision boundary-cut-voidstone-draws-without-its-stars
 * decision room-edge-draws-a-sparser-starfield
 */
public final class VoidstoneStars {

    private VoidstoneStars() {
    }

    /** How densely a voidstone draws its stars. */
    public enum Density {
        /** The voidstone's whole starfield. */
        FULL,
        /** A sparser starfield, on the block the boundary cuts. */
        SPARSE,
        /** No stars: the block's plain dark faces, wholly beyond the boundary. */
        BARE
    }

    /**
     * One floor column a room lays starred faces on.
     *
     * @param x       the column's x
     * @param z       the column's z
     * @param density how densely it draws its stars
     */
    public record Column(int x, int z, Density density) {
    }

    /**
     * How densely a voidstone draws its stars, by the room the client stands in now.
     *
     * @param x the block's x
     * @param z the block's z
     * @return its density, full where no room is known
     */
    public static Density density(int x, int z) {
        return ClientRoomEdges.bounds().map(bounds -> densityIn(bounds, x, z)).orElse(Density.FULL);
    }

    /**
     * How densely a voidstone draws its stars in a room.
     *
     * @param bounds the room's boundary
     * @param x      the block's x
     * @param z      the block's z
     * @return its density
     */
    static Density densityIn(RoomExtent.Bounds bounds, int x, int z) {
        if (bounds.holdsWholeBlock(x, z)) {
            return Density.FULL;
        }
        return bounds.cutsBlock(x, z) ? Density.SPARSE : Density.BARE;
    }

    /**
     * The floor columns a room lays starred faces on, each with its density:
     * the whole square, and the ring around it the boundary cuts where it
     * cuts one.
     *
     * @param bounds the room's boundary
     * @return the columns, west to east within each row, rows north to south
     */
    static List<Column> floorColumns(RoomExtent.Bounds bounds) {
        int reach = RoomExtent.wholeRings(bounds.extent()) + 1;
        List<Column> columns = new ArrayList<>();
        for (int z = bounds.centerZ() - reach; z <= bounds.centerZ() + reach; z++) {
            for (int x = bounds.centerX() - reach; x <= bounds.centerX() + reach; x++) {
                Density density = densityIn(bounds, x, z);
                if (density != Density.BARE) {
                    columns.add(new Column(x, z, density));
                }
            }
        }
        return columns;
    }
}

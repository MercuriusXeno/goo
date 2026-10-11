package com.mercuriusxeno.goo.ability.gate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Where an End gate lies and where it sets travellers down. The gate is a
 * two by two starfield square flat against the struck face, centred on the
 * struck block, so it overlaps each of the block's eight neighbours on that
 * face by half a block. It is held by a thin layer in the three by three
 * open cells in front of the face, each cell holding its share of the square.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public final class GateSquare {

    /** How thick the layer holding the square stands off the face, in blocks. */
    public static final double DEPTH = 1.0 / 16;
    /** How far the cells reach from the middle cell along each axis of the plane. */
    static final int REACH = 1;
    /** How far from the struck block a floor gate sets travellers down, past the square's edge. */
    static final int FLOOR_STEP_ASIDE = REACH + 1;
    /** How far below a ceiling gate travellers drop, so they hang clear of it. */
    static final int CEILING_DROP = 2;
    private static final double HALF = 0.5;
    /** The last place across the square, the cell farthest along an axis. */
    private static final int LAST_PLACE = REACH + REACH;
    private static final int AXES = Direction.Axis.values().length;

    private GateSquare() {
    }

    /**
     * One cell of the gate's layer and its place across the square.
     *
     * @param pos    the cell
     * @param across its place on the plane's first axis, 0 to 2, the middle 1
     * @param along  its place on the plane's second axis, 0 to 2, the middle 1
     */
    public record Cell(BlockPos pos, int across, int along) {
    }

    /**
     * The cells in front of a struck face that hold the gate.
     *
     * @param surface the struck block
     * @param face    the struck face
     * @return the nine cells, the middle one first
     */
    public static List<Cell> cells(BlockPos surface, Direction face) {
        BlockPos middle = surface.relative(face);
        Direction.Axis[] plane = planeOf(face);
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell(middle, REACH, REACH));
        for (int across = -REACH; across <= REACH; across++) {
            for (int along = -REACH; along <= REACH; along++) {
                if (across != 0 || along != 0) {
                    BlockPos pos = middle.relative(plane[0], across).relative(plane[1], along);
                    cells.add(new Cell(pos, across + REACH, along + REACH));
                }
            }
        }
        return cells;
    }

    /**
     * The share of the square a cell holds, as a thin box in the cell's own
     * coordinates against the struck face: the whole of the middle cell, half
     * of an edge cell and a quarter of a corner cell.
     *
     * @param face   the face the gate looks out of
     * @param across the cell's place on the plane's first axis
     * @param along  the cell's place on the plane's second axis
     * @return the box, from 0 to 1 on each axis
     */
    public static AABB cellLayer(Direction face, int across, int along) {
        Direction.Axis[] plane = planeOf(face);
        double[] low = new double[AXES];
        double[] high = new double[AXES];
        boolean outward = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int normal = face.getAxis().ordinal();
        low[normal] = outward ? 0 : 1 - DEPTH;
        high[normal] = outward ? DEPTH : 1;
        spanOf(plane[0], across, low, high);
        spanOf(plane[1], along, low, high);
        return new AABB(low[Direction.Axis.X.ordinal()], low[Direction.Axis.Y.ordinal()],
                low[Direction.Axis.Z.ordinal()], high[Direction.Axis.X.ordinal()], high[Direction.Axis.Y.ordinal()],
                high[Direction.Axis.Z.ordinal()]);
    }

    /**
     * Writes the share of a cell the square covers along one axis: the half
     * nearer the middle for a cell either side of it, the whole of the middle.
     *
     * @param axis  the axis
     * @param place the cell's place on it, 0 to 2
     * @param low   the box's low bounds, written
     * @param high  the box's high bounds, written
     */
    private static void spanOf(Direction.Axis axis, int place, double[] low, double[] high) {
        low[axis.ordinal()] = place == 0 ? HALF : 0;
        high[axis.ordinal()] = place == LAST_PLACE ? HALF : 1;
    }

    /**
     * The two axes of the plane a face lies in.
     *
     * @param face the face
     * @return the plane's first and second axis
     */
    public static Direction.Axis[] planeOf(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    /**
     * Where a gate sets a traveller's feet down: beside a floor gate on the
     * surface it lies on, under a ceiling gate, and in front of a wall gate.
     *
     * @param surface the gate's struck block
     * @param face    the face the gate looks out of
     * @return the traveller's feet
     */
    public static Vec3 arrival(BlockPos surface, Direction face) {
        BlockPos feet = switch (face) {
            case UP -> surface.above().east(FLOOR_STEP_ASIDE);
            case DOWN -> surface.below(CEILING_DROP);
            default -> surface.relative(face);
        };
        return Vec3.atBottomCenterOf(feet);
    }
}

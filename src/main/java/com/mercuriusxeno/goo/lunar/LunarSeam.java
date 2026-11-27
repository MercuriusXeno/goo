package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where the moon meets itself: lunar repeats every {@link #PERIOD} blocks on x and z, so a coordinate past one edge
 * reads as the same distance in from the other. Generation, the crossing and the dark side all read a coordinate's
 * wrap here, and generated features and structures keep clear of the seam, since the seam is the edge the world is
 * drawn across.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarSeam {

    /** The moon's way around on x and on z, in blocks: the seam stands at plus and minus half of it. */
    public static final int PERIOD = 2 * Lunar.BORDER_HALF_WIDTH;
    /**
     * How far a feature writes past the chunk it decorates, in blocks: one chunk each way, the farthest the world
     * generation region lets a feature reach.
     */
    static final int FEATURE_REACH = 16;
    private static final int HALVES = 2;
    private static final int HALF_PERIOD = PERIOD / HALVES;
    private static final int CHUNK_SIDE = 16;

    private LunarSeam() {
    }

    /**
     * A coordinate as the moon reads it, wrapped into [-7,500,000, 7,500,000): a step past one edge reads as a step
     * in from the other.
     *
     * @param coordinate an x or a z, in blocks
     * @return the same place inside the moon's span
     */
    public static double wrap(double coordinate) {
        return coordinate - PERIOD * Math.floor((coordinate + HALF_PERIOD) / PERIOD);
    }

    /**
     * A block coordinate as the moon reads it, wrapped into [-7,500,000, 7,500,000).
     *
     * @param coordinate an x or a z, in blocks
     * @return the same block inside the moon's span
     */
    public static int wrap(int coordinate) {
        return Math.floorMod(coordinate + HALF_PERIOD, PERIOD) - HALF_PERIOD;
    }

    /**
     * A grid cell as the moon reads it, so a cell past one edge rolls as the cell the same distance in from the
     * other. The cell's side divides the moon's way around, so the seam falls on a cell's edge.
     *
     * @param cell     the cell's index along x or z
     * @param cellSide the cell's side, in blocks, a divisor of {@link #PERIOD}
     * @return the same cell inside the moon's span
     */
    public static int wrapCell(int cell, int cellSide) {
        int cells = PERIOD / cellSide;
        int halfCells = cells / HALVES;
        return Math.floorMod(cell + halfCells, cells) - halfCells;
    }

    /**
     * Whether a footprint reaches across the seam on x or on z, holding blocks on both sides of it.
     *
     * @param footprint the blocks a feature or structure may set
     * @return true where the footprint crosses the seam
     */
    public static boolean crossesSeam(BoundingBox footprint) {
        return crossesAlong(footprint.minX(), footprint.maxX()) || crossesAlong(footprint.minZ(), footprint.maxZ());
    }

    /**
     * Whether a chunk's features may decorate it: none may where a feature could write across the seam.
     *
     * @param chunk the chunk being decorated
     * @return true where every block its features may write stays on one side of the seam
     */
    public static boolean featuresMayDecorate(ChunkPos chunk) {
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        return !crossesSeam(new BoundingBox(minX - FEATURE_REACH, 0, minZ - FEATURE_REACH,
                minX + CHUNK_SIDE - 1 + FEATURE_REACH, 0, minZ + CHUNK_SIDE - 1 + FEATURE_REACH));
    }

    /**
     * Whether a structure may stand where its pieces lie: none may within a feature's reach of the seam, where its
     * chunks would go undecorated.
     *
     * @param pieces the box holding the structure's pieces
     * @return true where the structure and the reach around it stay on one side of the seam
     */
    public static boolean structureMayStand(BoundingBox pieces) {
        return !crossesSeam(pieces.inflatedBy(FEATURE_REACH, 0, FEATURE_REACH));
    }

    private static boolean crossesAlong(int min, int max) {
        return Math.floorDiv(min + HALF_PERIOD, PERIOD) != Math.floorDiv(max + HALF_PERIOD, PERIOD);
    }
}

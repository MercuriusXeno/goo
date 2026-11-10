package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * How a block comes apart into chunks: the block's outline cut into a grid of
 * cells, neighbouring cells merged at random into rigid chunks of mixed size,
 * half a block, a slab, a bar or a lone cell, so the block breaks into a few
 * irregular chunks rather than a staircase of little cubes; each cell's face
 * wears its own cell of the block's face, so the chunks standing together are
 * the block. The chunks crumble off in random order, each from the outside of
 * what still stands, so the block erodes unevenly rather than sweeping off in
 * a staircase, their turns spread over the crumble. A cell's face shows only
 * where it bounds its chunk and no standing chunk covers it, so a whole block
 * draws as its shell.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class BlockBreakup {

    /** Cells along each edge of the block, and along each edge of each of its faces' textures. */
    public static final int CELLS_ACROSS = 4;
    /** Cells a block is cut into. */
    public static final int CELLS = CELLS_ACROSS * CELLS_ACROSS * CELLS_ACROSS;
    /** The most cells a chunk spans along any edge. */
    public static final int LONGEST_SPAN = 2;
    private static final double HALF = 0.5;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    /**
     * The chunk shapes a cell may start, in cells along x, y and z, biggest first, each tried at its chance and
     * the lone cell always taken.
     */
    private static final int[][] SHAPES = {
        {2, 2, 2}, {2, 2, 1}, {2, 1, 2}, {1, 2, 2}, {2, 1, 1}, {1, 2, 1}, {1, 1, 2}, {1, 1, 1},
    };
    /**
     * The chance each of {@link #SHAPES} is taken where it fits: high for the big ones, so a block breaks into few
     * chunks.
     */
    private static final double[] CHANCES = {0.6, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 1};

    private BlockBreakup() {
    }

    /**
     * One chunk of a block coming apart: a box of the block's cells.
     *
     * @param place   its middle while it stands in the block
     * @param least   the grid place of its least cell, along x, y and z
     * @param span    the cells it spans along x, y and z
     * @param release the game time its turn comes and it leaves the block
     */
    public record Chunk(Vec3 place, int[] least, int[] span, double release) {

        /**
         * @return how many cells it holds
         */
        public int cells() {
            return span[X] * span[Y] * span[Z];
        }
    }

    /**
     * How a block breaks up.
     *
     * @param chunks      its chunks, in the order they leave
     * @param chunkOfCell by grid index, the index in chunks of the chunk holding that cell
     * @param least       the least corner of the block's outline in the world
     * @param cellSize    one cell's edges along x, y and z, in blocks
     */
    public record Breakup(List<Chunk> chunks, int[] chunkOfCell, Vec3 least, Vec3 cellSize) {

        /**
         * @param cell a grid index
         * @return the middle of that cell while its chunk stands
         */
        public Vec3 placeOf(int cell) {
            return least.add(cellSize.x * (xOf(cell) + HALF), cellSize.y * (yOf(cell) + HALF),
                    cellSize.z * (zOf(cell) + HALF));
        }
    }

    /**
     * The cell of a block face's texture a cell's face wears, laid as vanilla
     * lays a cube's face textures, so the cells' faces together show the
     * block's face whole.
     *
     * @param across the cell's column, from the texture's left
     * @param down   the cell's row, from the texture's top
     */
    public record FaceCell(int across, int down) {
    }

    /**
     * @param x a column along x
     * @param y a layer along y
     * @param z a row along z
     * @return the grid index of that place
     */
    public static int cellAt(int x, int y, int z) {
        return (x * CELLS_ACROSS + y) * CELLS_ACROSS + z;
    }

    /**
     * @param cell a grid index
     * @return its column along x
     */
    static int xOf(int cell) {
        return cell / (CELLS_ACROSS * CELLS_ACROSS);
    }

    /**
     * @param cell a grid index
     * @return its layer along y
     */
    static int yOf(int cell) {
        return cell / CELLS_ACROSS % CELLS_ACROSS;
    }

    /**
     * @param cell a grid index
     * @return its row along z
     */
    static int zOf(int cell) {
        return cell % CELLS_ACROSS;
    }

    /**
     * @param pos     where the block stands
     * @param start   the game time its first chunk leaves
     * @param end     the game time the crumble ends
     * @param outline the block's outline, within its cell
     * @param random  the random that merges its cells and picks which crumbles off next
     * @return how the block breaks up: its cells merged into chunks filling its outline, crumbling off in random
     *         order, each from the outside of what still stands, their turns spread from start, every one before end
     */
    public static Breakup chunksOf(BlockPos pos, long start, long end, AABB outline, RandomSource random) {
        AABB box = outline.move(pos);
        Vec3 least = new Vec3(box.minX, box.minY, box.minZ);
        Vec3 cellSize = new Vec3(box.getXsize(), box.getYsize(), box.getZsize()).scale(1.0 / CELLS_ACROSS);
        List<int[][]> boxes = crumbleOrder(mergedCells(random), random);
        double crumble = Math.max(1, end - start);
        List<Chunk> chunks = new ArrayList<>(boxes.size());
        int[] chunkOfCell = new int[CELLS];
        for (int turn = 0; turn < boxes.size(); turn++) {
            int[][] cells = boxes.get(turn);
            chunks.add(new Chunk(middleOf(least, cellSize, cells), cells[0], cells[1],
                    start + crumble * turn / boxes.size()));
            claim(chunkOfCell, cells, turn);
        }
        return new Breakup(chunks, chunkOfCell, least, cellSize);
    }

    /**
     * @param random the random that merges the cells
     * @return the grid's cells merged into boxes, each its least cell and its span along x, y and z
     */
    private static List<int[][]> mergedCells(RandomSource random) {
        boolean[] taken = new boolean[CELLS];
        List<int[][]> boxes = new ArrayList<>();
        for (int cell = 0; cell < CELLS; cell++) {
            if (!taken[cell]) {
                int[] least = {xOf(cell), yOf(cell), zOf(cell)};
                int[] span = shapeAt(least, taken, random);
                int[][] merged = {least, span};
                boxes.add(merged);
                claim(taken, merged);
            }
        }
        return boxes;
    }

    /**
     * Orders the boxes as they crumble off: each turn, one picked at random
     * from those on the outside of what still stands, touching the block's
     * outline or a box already gone, so the block erodes unevenly rather than
     * sweeping off in steps.
     *
     * @param boxes  the merged boxes, in any order
     * @param random the random that picks each next box
     * @return the boxes in the order they leave
     */
    private static List<int[][]> crumbleOrder(List<int[][]> boxes, RandomSource random) {
        int[] boxOfCell = new int[CELLS];
        for (int index = 0; index < boxes.size(); index++) {
            claim(boxOfCell, boxes.get(index), index);
        }
        boolean[] gone = new boolean[boxes.size()];
        List<int[][]> order = new ArrayList<>(boxes.size());
        while (order.size() < boxes.size()) {
            List<Integer> outside = new ArrayList<>();
            for (int index = 0; index < boxes.size(); index++) {
                if (!gone[index] && onOutside(boxes.get(index), boxOfCell, gone)) {
                    outside.add(index);
                }
            }
            int next = outside.get(random.nextInt(outside.size()));
            gone[next] = true;
            order.add(boxes.get(next));
        }
        return order;
    }

    /**
     * @param box       a box still standing
     * @param boxOfCell by grid index, the box holding that cell
     * @param gone      by box index, whether that box has left
     * @return whether one of its cells touches the block's outline or a box already gone
     */
    static boolean onOutside(int[][] box, int[] boxOfCell, boolean[] gone) {
        for (int cell : cellsOf(box)) {
            for (Direction face : Direction.values()) {
                if (bared(cell, face, boxOfCell, gone)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param cell      a grid index
     * @param face      one of that cell's faces
     * @param boxOfCell by grid index, the box holding that cell
     * @param gone      by box index, whether that box has left
     * @return whether the face looks onto the block's outline or onto a box already gone
     */
    private static boolean bared(int cell, Direction face, int[] boxOfCell, boolean[] gone) {
        int x = xOf(cell) + face.getStepX();
        int y = yOf(cell) + face.getStepY();
        int z = zOf(cell) + face.getStepZ();
        return !inGrid(x) || !inGrid(y) || !inGrid(z) || gone[boxOfCell[cellAt(x, y, z)]];
    }

    /**
     * @param least  the free cell a chunk starts at
     * @param taken  by grid index, whether a chunk holds the cell already
     * @param random the random that picks the shape
     * @return the span of the first shape that fits there and is taken at its chance
     */
    private static int[] shapeAt(int[] least, boolean[] taken, RandomSource random) {
        for (int shape = 0; shape < SHAPES.length; shape++) {
            if (fits(least, SHAPES[shape], taken) && random.nextDouble() < CHANCES[shape]) {
                return SHAPES[shape];
            }
        }
        return SHAPES[SHAPES.length - 1];
    }

    /**
     * @param least the cell a box starts at
     * @param span  its span along x, y and z
     * @param taken by grid index, whether a chunk holds the cell already
     * @return whether every cell of the box lies in the grid and is free
     */
    private static boolean fits(int[] least, int[] span, boolean[] taken) {
        if (!inGrid(least[X] + span[X] - 1) || !inGrid(least[Y] + span[Y] - 1) || !inGrid(least[Z] + span[Z] - 1)) {
            return false;
        }
        for (int cell : cellsOf(new int[][] {least, span})) {
            if (taken[cell]) {
                return false;
            }
        }
        return true;
    }

    private static void claim(boolean[] taken, int[][] box) {
        for (int cell : cellsOf(box)) {
            taken[cell] = true;
        }
    }

    private static void claim(int[] chunkOfCell, int[][] box, int chunk) {
        for (int cell : cellsOf(box)) {
            chunkOfCell[cell] = chunk;
        }
    }

    /**
     * @param box a box's least cell and its span
     * @return the grid index of every cell of the box
     */
    private static int[] cellsOf(int[][] box) {
        int[] least = box[0];
        int[] span = box[1];
        int[] cells = new int[span[X] * span[Y] * span[Z]];
        int at = 0;
        for (int x = 0; x < span[X]; x++) {
            for (int y = 0; y < span[Y]; y++) {
                for (int z = 0; z < span[Z]; z++) {
                    cells[at++] = cellAt(least[X] + x, least[Y] + y, least[Z] + z);
                }
            }
        }
        return cells;
    }

    private static Vec3 middleOf(Vec3 least, Vec3 cellSize, int[][] box) {
        int[] from = box[0];
        int[] span = box[1];
        return least.add(cellSize.x * (from[X] + span[X] * HALF), cellSize.y * (from[Y] + span[Y] * HALF),
                cellSize.z * (from[Z] + span[Z] * HALF));
    }

    /**
     * @param chunk a chunk
     * @param now   the game time, with the partial tick
     * @return whether it still stands in the block, until the moment after its turn
     */
    public static boolean stands(Chunk chunk, double now) {
        return now <= chunk.release();
    }

    /**
     * @param chunks a block's chunks
     * @param now    the game time, with the partial tick
     * @return by chunk index, whether that chunk still stands
     */
    public static boolean[] standingOf(List<Chunk> chunks, double now) {
        boolean[] standing = new boolean[chunks.size()];
        for (int index = 0; index < standing.length; index++) {
            standing[index] = stands(chunks.get(index), now);
        }
        return standing;
    }

    /**
     * @param breakup  how the block breaks up
     * @param cell     a grid index
     * @param face     one of that cell's faces
     * @param standing by chunk index, whether that chunk still stands
     * @return whether the face shows: where it bounds its chunk, unless its chunk and the chunk beyond both stand
     */
    public static boolean shows(Breakup breakup, int cell, Direction face, boolean[] standing) {
        int x = xOf(cell) + face.getStepX();
        int y = yOf(cell) + face.getStepY();
        int z = zOf(cell) + face.getStepZ();
        if (!inGrid(x) || !inGrid(y) || !inGrid(z)) {
            return true;
        }
        int own = breakup.chunkOfCell()[cell];
        int beyond = breakup.chunkOfCell()[cellAt(x, y, z)];
        return own != beyond && !(standing[own] && standing[beyond]);
    }

    /**
     * @param place a place along one edge of the grid
     * @return whether it falls within the grid
     */
    private static boolean inGrid(int place) {
        return place >= 0 && place < CELLS_ACROSS;
    }

    /**
     * @param cell a grid index
     * @param face one of its faces
     * @return the cell of the block's face texture that face wears
     */
    public static FaceCell faceCellOf(int cell, Direction face) {
        FaceLayout layout = FaceLayout.of(face);
        int[] place = {xOf(cell), yOf(cell), zOf(cell)};
        int last = CELLS_ACROSS - 1;
        return new FaceCell(layout.across(place, last), layout.down(place, last));
    }

    /**
     * How vanilla lays a texture on one face of a cube: which axis runs
     * across the texture and which down it, and whether each runs against
     * the axis.
     *
     * @param acrossAxis    the axis running across, 0 for x, 1 for y, 2 for z
     * @param acrossAgainst whether the texture runs across against that axis
     * @param downAxis      the axis running down
     * @param downAgainst   whether the texture runs down against that axis
     */
    public record FaceLayout(int acrossAxis, boolean acrossAgainst, int downAxis, boolean downAgainst) {

        /** Each face's layout, by {@link Direction#get3DDataValue()}. */
        private static final FaceLayout[] BY_FACE = {
            new FaceLayout(X, false, Z, true),
            new FaceLayout(X, false, Z, false),
            new FaceLayout(X, true, Y, true),
            new FaceLayout(X, false, Y, true),
            new FaceLayout(Z, false, Y, true),
            new FaceLayout(Z, true, Y, true),
        };

        /**
         * @param face a face of a cube
         * @return how vanilla lays a texture on it
         */
        public static FaceLayout of(Direction face) {
            return BY_FACE[face.get3DDataValue()];
        }

        /**
         * @param place a place along x, y and z, each 0 to last
         * @param last  the furthest place along an axis
         * @return how far across the texture the place falls, 0 to last
         */
        public int across(int[] place, int last) {
            return acrossAgainst ? last - place[acrossAxis] : place[acrossAxis];
        }

        /**
         * @param place a place along x, y and z, each 0 to last
         * @param last  the furthest place along an axis
         * @return how far down the texture the place falls, 0 to last
         */
        public int down(int[] place, int last) {
            return downAgainst ? last - place[downAxis] : place[downAxis];
        }
    }
}

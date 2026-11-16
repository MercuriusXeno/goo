package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where the glass shards over one Shatter cell stand: each tile draws three
 * to five shards from its seed, and spreads them evenly at random, each
 * shard's center in a square of a 3 by 3 grid over the whole tile, edge to
 * edge, no other of its shards takes, at a random spot inside that square; a
 * shard near an edge may reach over it into the next block. Each rests whole
 * just above the goo, at a random height in a thin band over it, so a thicker
 * cell lifts its shards with its goo. A cell's shown share lifts each shard out of the
 * goo to its resting place, from wholly under the surface at none to at rest
 * at all of it, and fades it in from full transparency as it rises.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 * razor-shards-are-bigger
 */
final class ShatterCellShards {

    /** The layers a whole block of goo holds, the cell's surface rising a fifteenth a layer. */
    static final int LAYERS_PER_BLOCK = 15;
    /** The fewest shards a tile draws. */
    static final int FEWEST_SHARDS = 3;
    /** The most shards a tile draws. */
    static final int MOST_SHARDS = 5;
    /** The squares along each side of the grid a tile's shards spread over. */
    static final int GRID = 3;
    /** How far a shard's reach stands off its long arm, so the pyramid's short arms and apex fit inside it. */
    private static final float REACH_SLACK = 1.05f;
    /** The least gap a resting shard keeps over the goo's surface, in blocks. */
    private static final float CLEARANCE = 0.02f;
    /** The height of the thin band over that gap the shards' lowest points rest through, in blocks. */
    private static final float HANG_BAND = 0.1f;
    /** The most a resting shard's lowest point stands over the goo's surface, in blocks. */
    static final float RESTING_GAP = CLEARANCE + HANG_BAND;
    /** The share of its square a shard's spot may stand anywhere in, keeping it off its neighbors' edges. */
    private static final float SQUARE_PLAY = 0.8f;
    private static final float HALF = 0.5f;
    /** A spot runs both ways from its square's middle. */
    private static final int BOTH_SIDES = 2;

    private ShatterCellShards() {
    }

    /**
     * One tile's shards as its seed draws them: their shapes, tilts and spins,
     * and for each the grid square it stands in, its spot inside that square
     * across and along the tile, each from -1 to 1, and its height in the band
     * over the goo, from 0 to 1.
     *
     * @param shapes  the shards' shapes, tilts and spins
     * @param squares the grid square each shard stands in, row by row
     * @param across  each shard's spot across its square, -1 to 1
     * @param along   each shard's spot along its square, -1 to 1
     * @param heights each shard's height in the band, 0 to 1
     */
    record CellLayout(ShardTable shapes, int[] squares, float[] across, float[] along, float[] heights) {

        /**
         * @return the shards the tile draws
         */
        int size() {
            return squares.length;
        }
    }

    /**
     * Draws one tile's layout from a seed: two to four shards, each in its own
     * square of the grid, at a random spot, height, shape, tilt and spin; one
     * seed always draws one layout.
     *
     * @param seed the seed the layout is drawn from
     * @return the layout
     */
    static CellLayout layoutFromSeed(long seed) {
        Random rng = new Random(seed);
        int count = FEWEST_SHARDS + rng.nextInt(MOST_SHARDS - FEWEST_SHARDS + 1);
        List<Integer> free = new ArrayList<>();
        for (int square = 0; square < GRID * GRID; square++) {
            free.add(square);
        }
        int[] squares = new int[count];
        float[] across = new float[count];
        float[] along = new float[count];
        float[] heights = new float[count];
        for (int shard = 0; shard < count; shard++) {
            squares[shard] = free.remove(rng.nextInt(free.size()));
            across[shard] = spotFrom(rng);
            along[shard] = spotFrom(rng);
            heights[shard] = rng.nextFloat();
        }
        return new CellLayout(ShardTable.fromSeed(rng.nextLong(), count), squares, across, along, heights);
    }

    /**
     * A spot inside a grid square drawn from a tile's random, -1 to 1.
     *
     * @param rng the tile's random
     * @return the spot
     */
    private static float spotFrom(Random rng) {
        return (rng.nextFloat() - HALF) * BOTH_SIDES;
    }

    /**
     * One shard over a cell this frame.
     *
     * @param index   the shard's index in the cell's layout
     * @param center  the shard's center in world coordinates
     * @param reach   the furthest any point of the shard stands from its center, in blocks
     * @param opacity the shard's opacity share, 0 fully transparent to 1 full
     */
    record CellShard(int index, Vector3fc center, float reach, float opacity) {
    }

    /**
     * The shards over a cell at a shown share.
     *
     * @param cell   the cell's block position
     * @param layers the layers the cell holds
     * @param shown  how far the cell's shards have risen out of the goo, 0 hidden to 1 at rest
     * @param layout the cell's layout
     * @return every shard the layout holds, at its place this frame
     */
    static List<CellShard> shardsOver(BlockPos cell, int layers, float shown, CellLayout layout) {
        float surface = (float) layers / LAYERS_PER_BLOCK;
        List<CellShard> shards = new ArrayList<>(layout.size());
        for (int shard = 0; shard < layout.size(); shard++) {
            float reach = layout.shapes().halfLength(shard) * REACH_SLACK;
            float rest = surface + reach + CLEARANCE + layout.heights()[shard] * HANG_BAND;
            float underTheGoo = surface - reach;
            int square = layout.squares()[shard];
            Vector3f center = new Vector3f(
                    cell.getX() + spotOnTheTile(square / GRID, layout.across()[shard]),
                    cell.getY() + underTheGoo + shown * (rest - underTheGoo),
                    cell.getZ() + spotOnTheTile(square % GRID, layout.along()[shard]));
            shards.add(new CellShard(shard, center, reach, shown));
        }
        return shards;
    }

    /**
     * Where a shard's center stands along one side of the tile: in its
     * square of the grid over the whole tile, at its spot inside the square.
     *
     * @param square the square's index along that side
     * @param spot   the shard's spot inside the square, -1 to 1
     * @return the center's offset from the tile's side, in blocks
     */
    private static float spotOnTheTile(int square, float spot) {
        return (square + HALF + spot * HALF * SQUARE_PLAY) / GRID;
    }
}

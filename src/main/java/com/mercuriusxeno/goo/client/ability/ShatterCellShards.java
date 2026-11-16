package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where the glass shards over one Shatter cell stand: each tile draws two to
 * four shards from its seed, and spreads them evenly at random, each shard in
 * a square of a 3 by 3 grid over the tile no other of its shards takes, at a
 * random spot inside that square and a random height in a band over the goo,
 * whole above the surface within the tile's column, so a thicker cell lifts
 * its shards with its goo. A cell's shown share lifts each shard out of the
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
    static final int FEWEST_SHARDS = 2;
    /** The most shards a tile draws. */
    static final int MOST_SHARDS = 4;
    /** The squares along each side of the grid a tile's shards spread over. */
    static final int GRID = 3;
    /** How far a shard's reach stands off its long arm, so the pyramid's short arms and apex fit inside it. */
    private static final float REACH_SLACK = 1.05f;
    /** The gap a shard keeps from the cell's surface and the column's sides, in blocks. */
    private static final float CLEARANCE = 0.03f;
    /** The height of the band over the surface the shards' resting centers spread through, in blocks. */
    private static final float HANG_BAND = 0.4f;
    /** The share of its square a shard's spot may stand anywhere in, keeping it off its neighbors' edges. */
    private static final float SQUARE_PLAY = 0.8f;
    private static final float HALF = 0.5f;
    /** A column's two sides, each keeping a shard's reach and clearance clear. */
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
     * The low edge of the span a shard's center may stand in across the
     * tile, its reach and clearance in from the column's side, so the whole
     * shard stays inside the column; the high edge mirrors it.
     *
     * @param reach the shard's reach, in blocks
     * @return the span's low edge, in blocks from the column's side
     */
    static float spanLow(float reach) {
        return reach + CLEARANCE;
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
                    cell.getX() + spotInSpan(square / GRID, layout.across()[shard], reach),
                    cell.getY() + underTheGoo + shown * (rest - underTheGoo),
                    cell.getZ() + spotInSpan(square % GRID, layout.along()[shard], reach));
            shards.add(new CellShard(shard, center, reach, shown));
        }
        return shards;
    }

    /**
     * Where a shard's center stands along one side of the tile: in its
     * square of the span its reach leaves it, at its spot inside the square.
     *
     * @param square the square's index along that side
     * @param spot   the shard's spot inside the square, -1 to 1
     * @param reach  the shard's reach, in blocks
     * @return the center's offset from the column's side, in blocks
     */
    private static float spotInSpan(int square, float spot, float reach) {
        float low = spanLow(reach);
        float squareWidth = (1f - BOTH_SIDES * low) / GRID;
        return low + (square + HALF + spot * HALF * SQUARE_PLAY) * squareWidth;
    }
}

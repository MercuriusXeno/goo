package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the glass shards over one Shatter cell stand: each shard of the
 * cell's layout rests whole in the space above the cell's goo surface within
 * its block column, its seeded spot in the unit sphere spread across the
 * column and up a band over the surface, so a thicker cell lifts its shards
 * with its goo. A cell's shown share lifts each shard out of the goo to its
 * resting place, from wholly under the surface at none to at rest at all of
 * it, and fades it in from full transparency as it rises.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 * razor-shards-are-bigger
 */
final class ShatterCellShards {

    /** The layers a whole block of goo holds, the cell's surface rising a fifteenth a layer. */
    static final int LAYERS_PER_BLOCK = 15;
    /** The shards one cell's layout holds. */
    static final int SHARDS_PER_CELL = 5;
    /** How far a shard's reach stands off its long arm, so the pyramid's short arms and apex fit inside it. */
    private static final float REACH_SLACK = 1.05f;
    /** The gap a shard keeps from the cell's surface and the column's sides, in blocks. */
    private static final float CLEARANCE = 0.03f;
    /** The height of the band over the surface the shards' resting centers spread through, in blocks. */
    private static final float HANG_BAND = 0.4f;
    private static final float HALF = 0.5f;

    private ShatterCellShards() {
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
     * @param layout the cell's shard layout
     * @return every shard the layout holds, at its place this frame
     */
    static List<CellShard> shardsOver(BlockPos cell, int layers, float shown, ShardTable layout) {
        float surface = (float) layers / LAYERS_PER_BLOCK;
        List<CellShard> shards = new ArrayList<>(layout.size());
        for (int shard = 0; shard < layout.size(); shard++) {
            float reach = layout.halfLength(shard) * REACH_SLACK;
            Vector3f seeded = layout.center(shard);
            float spread = HALF - reach - CLEARANCE;
            float rest = surface + reach + CLEARANCE + (seeded.y() + 1f) * HALF * HANG_BAND;
            float underTheGoo = surface - reach;
            Vector3f center = new Vector3f(cell.getX() + HALF + seeded.x() * spread,
                    cell.getY() + underTheGoo + shown * (rest - underTheGoo),
                    cell.getZ() + HALF + seeded.z() * spread);
            shards.add(new CellShard(shard, center, reach, shown));
        }
        return shards;
    }
}

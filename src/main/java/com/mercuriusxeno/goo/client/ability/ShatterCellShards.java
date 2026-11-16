package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the glass shards over one Shatter cell stand: each shard of the
 * cell's layout hangs whole in the space above the cell's goo surface within
 * its block column, its seeded spot in the unit sphere spread across the
 * column and up a band over the surface, so a thicker cell lifts its shards
 * with its goo. As the goo lands each shard fades in from full transparency,
 * drifting a short way down to its resting place.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 * razor-shards-are-bigger
 */
final class ShatterCellShards {

    /** The layers a whole block of goo holds, the cell's surface rising a fifteenth a layer. */
    static final int LAYERS_PER_BLOCK = 15;
    /** The shards one cell's layout holds. */
    static final int SHARDS_PER_CELL = 5;
    /** Ticks a shard takes to fade in and drift to rest once its goo lands. */
    static final float FADE_TICKS = 20f;
    /** How far above its resting place a shard is born, in blocks. */
    static final float DRIFT = 0.2f;
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
     * The shards over a cell, a time after its goo landed.
     *
     * @param cell              the cell's block position
     * @param layers            the layers the cell holds
     * @param ticksSinceLanding the ticks since the cell's goo landed, partial tick included
     * @param layout            the cell's shard layout
     * @return every shard the layout holds, at its place this frame
     */
    static List<CellShard> shardsOver(BlockPos cell, int layers, float ticksSinceLanding, ShardTable layout) {
        float landed = landedShare(ticksSinceLanding);
        float surface = (float) layers / LAYERS_PER_BLOCK;
        List<CellShard> shards = new ArrayList<>(layout.size());
        for (int shard = 0; shard < layout.size(); shard++) {
            float reach = layout.halfLength(shard) * REACH_SLACK;
            Vector3f seeded = layout.center(shard);
            float spread = HALF - reach - CLEARANCE;
            float rest = surface + reach + CLEARANCE + (seeded.y() + 1f) * HALF * HANG_BAND;
            Vector3f center = new Vector3f(cell.getX() + HALF + seeded.x() * spread,
                    cell.getY() + rest + (1f - landed) * DRIFT,
                    cell.getZ() + HALF + seeded.z() * spread);
            shards.add(new CellShard(shard, center, reach, landed));
        }
        return shards;
    }

    /**
     * How far a shard has faded in and drifted to rest: none as its goo
     * lands, all of it once its fade has run.
     *
     * @param ticksSinceLanding the ticks since the cell's goo landed
     * @return the share, 0 to 1
     */
    static float landedShare(float ticksSinceLanding) {
        return Mth.clamp(ticksSinceLanding / FADE_TICKS, 0f, 1f);
    }
}

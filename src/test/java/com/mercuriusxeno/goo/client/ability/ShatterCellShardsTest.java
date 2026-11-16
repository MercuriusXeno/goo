package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shards stand over each Shatter cell: every shard a cell answers lies whole
 * in the space above its goo's surface within its block column, and fades in
 * from full transparency as the goo lands, drifting to rest.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 */
class ShatterCellShardsTest {

    private static final BlockPos CELL = new BlockPos(-37, 64, 112);
    private static final float LANDED = 0f;
    private static final float FADED = ShatterCellShards.FADE_TICKS;
    private static final float EPSILON = 1e-4f;
    /** One cell's layout, drawn from a fixed seed so every case reads the same shards. */
    private static final ShardTable LAYOUT = ShardTable.fromSeed(7L, ShatterCellShards.SHARDS_PER_CELL);

    @Nested
    class Placement {

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 14, 15})
        void everyShardLiesWholeAboveTheSurfaceWithinTheCellsColumn(int layers) {
            List<ShatterCellShards.CellShard> shards = shardsOver(layers, FADED);
            float surface = CELL.getY() + (float) layers / ShatterCellShards.LAYERS_PER_BLOCK;

            for (ShatterCellShards.CellShard shard : shards) {
                float reach = shard.reach();
                assertTrue(shard.center().y() - reach > surface,
                        "a shard reaches down to " + (shard.center().y() - reach) + " under the surface " + surface);
                assertTrue(shard.center().x() - reach >= CELL.getX() && shard.center().x() + reach <= CELL.getX() + 1,
                        "a shard leaves the column across x at " + shard.center().x());
                assertTrue(shard.center().z() - reach >= CELL.getZ() && shard.center().z() + reach <= CELL.getZ() + 1,
                        "a shard leaves the column across z at " + shard.center().z());
            }
        }

        @Test
        void oneCellDrawsOneLayoutWhateverItsLayers() {
            List<ShatterCellShards.CellShard> thin = shardsOver(2, FADED);
            List<ShatterCellShards.CellShard> thick = shardsOver(9, FADED);

            assertEquals(thin.size(), thick.size());
            for (int shard = 0; shard < thin.size(); shard++) {
                assertEquals(thin.get(shard).center().x(), thick.get(shard).center().x(), EPSILON);
                assertTrue(thick.get(shard).center().y() > thin.get(shard).center().y(),
                        "a thicker cell's shards stand no higher");
            }
        }
    }

    @Nested
    class Fade {

        @Test
        void aShardIsFullyTransparentAsTheGooLandsAndWholeOnceItsFadeHasRun() {
            for (ShatterCellShards.CellShard shard : shardsOver(3, LANDED)) {
                assertEquals(0f, shard.opacity(), EPSILON);
            }
            for (ShatterCellShards.CellShard shard : shardsOver(3, FADED)) {
                assertEquals(1f, shard.opacity(), EPSILON);
            }
        }

        @Test
        void aFadingShardDriftsDownToRest() {
            List<ShatterCellShards.CellShard> landing = shardsOver(3, LANDED);
            List<ShatterCellShards.CellShard> halfway = shardsOver(3, FADED / 2);
            List<ShatterCellShards.CellShard> resting = shardsOver(3, FADED);

            for (int shard = 0; shard < resting.size(); shard++) {
                float atRest = resting.get(shard).center().y();
                assertTrue(landing.get(shard).center().y() > halfway.get(shard).center().y()
                        && halfway.get(shard).center().y() > atRest, "shard " + shard + " drifts no way down");
                assertTrue(landing.get(shard).center().y() - atRest <= ShatterCellShards.DRIFT + EPSILON,
                        "shard " + shard + " drifts further than a short way");
            }
        }
    }

    /** The shards over the cell, failing where a cell answers none, so no assertion over them passes empty. */
    private static List<ShatterCellShards.CellShard> shardsOver(int layers, float ticksSinceLanding) {
        List<ShatterCellShards.CellShard> shards = ShatterCellShards.shardsOver(CELL, layers, ticksSinceLanding, LAYOUT);
        assertFalse(shards.isEmpty(), "a cell of " + layers + " layers answers no shard");
        return shards;
    }
}

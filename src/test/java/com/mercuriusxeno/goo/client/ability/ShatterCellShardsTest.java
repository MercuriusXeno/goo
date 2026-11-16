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
 * Shards stand over a Shatter cell while something living is near it: every
 * shard of a cell fully shown lies whole in the space above its goo surface
 * within its block column, and a shard rises out of the goo, fading in from
 * full transparency, as its cell's shown share grows.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * razor-shards-appear-in-layers-ahead-of-the-dome
 */
class ShatterCellShardsTest {

    private static final BlockPos CELL = new BlockPos(-37, 64, 112);
    private static final float HIDDEN = 0f;
    private static final float HALF_SHOWN = 0.5f;
    private static final float SHOWN = 1f;
    private static final float EPSILON = 1e-4f;
    /** One cell's layout, drawn from a fixed seed so every case reads the same shards. */
    private static final ShardTable LAYOUT = ShardTable.fromSeed(7L, ShatterCellShards.SHARDS_PER_CELL);

    @Nested
    class Placement {

        @ParameterizedTest
        @ValueSource(ints = {1, 7, 14, 15})
        void everyShardOfAShownCellLiesWholeAboveTheSurfaceWithinItsColumn(int layers) {
            float surface = surfaceOf(layers);

            for (ShatterCellShards.CellShard shard : shardsOver(layers, SHOWN)) {
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
            List<ShatterCellShards.CellShard> thin = shardsOver(2, SHOWN);
            List<ShatterCellShards.CellShard> thick = shardsOver(9, SHOWN);

            assertEquals(thin.size(), thick.size());
            for (int shard = 0; shard < thin.size(); shard++) {
                assertEquals(thin.get(shard).center().x(), thick.get(shard).center().x(), EPSILON);
                assertTrue(thick.get(shard).center().y() > thin.get(shard).center().y(),
                        "a thicker cell's shards stand no higher");
            }
        }
    }

    @Nested
    class LiftAndFade {

        @Test
        void aHiddenShardSitsUnderTheGooFullyTransparentAndAShownOneAtFullOpacity() {
            float surface = surfaceOf(3);
            for (ShatterCellShards.CellShard shard : shardsOver(3, HIDDEN)) {
                assertEquals(0f, shard.opacity(), EPSILON);
                assertTrue(shard.center().y() + shard.reach() <= surface + EPSILON,
                        "a hidden shard reaches up to " + (shard.center().y() + shard.reach()) + " over the goo");
            }
            for (ShatterCellShards.CellShard shard : shardsOver(3, SHOWN)) {
                assertEquals(1f, shard.opacity(), EPSILON);
            }
        }

        @Test
        void aShardRisesOutOfTheGooAndFadesInAsItsShareGrows() {
            List<ShatterCellShards.CellShard> hidden = shardsOver(3, HIDDEN);
            List<ShatterCellShards.CellShard> halfway = shardsOver(3, HALF_SHOWN);
            List<ShatterCellShards.CellShard> shown = shardsOver(3, SHOWN);

            for (int shard = 0; shard < shown.size(); shard++) {
                assertTrue(hidden.get(shard).center().y() < halfway.get(shard).center().y()
                        && halfway.get(shard).center().y() < shown.get(shard).center().y(),
                        "shard " + shard + " rises no way out of the goo");
                assertTrue(hidden.get(shard).opacity() < halfway.get(shard).opacity()
                        && halfway.get(shard).opacity() < shown.get(shard).opacity(),
                        "shard " + shard + " fades in no way");
            }
        }
    }

    private static float surfaceOf(int layers) {
        return CELL.getY() + (float) layers / ShatterCellShards.LAYERS_PER_BLOCK;
    }

    /** The shards over the cell, failing where a cell answers none, so no assertion over them passes empty. */
    private static List<ShatterCellShards.CellShard> shardsOver(int layers, float shown) {
        List<ShatterCellShards.CellShard> shards = ShatterCellShards.shardsOver(CELL, layers, shown, LAYOUT);
        assertFalse(shards.isEmpty(), "a cell of " + layers + " layers answers no shard");
        return shards;
    }
}

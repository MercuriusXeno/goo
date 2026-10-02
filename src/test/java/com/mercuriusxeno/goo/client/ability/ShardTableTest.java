package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cloud's shard layout follows its seed: two seeds draw two layouts, one
 * seed draws one (decision cloud-seed-per-client-ephemeral), and every
 * layout draws shards at the doubled razor size (decision razor-shards-are-bigger).
 */
class ShardTableTest {

    private static final long[] SIZE_SEEDS = {1L, 2L, 3L, 42L, -7L, Long.MAX_VALUE};
    /**
     * Twice the 0.04 block half-length floor the 26.1 base drew.
     */
    private static final float DOUBLED_HALF_LENGTH_FLOOR = 0.08f;
    /**
     * The 26.1 base's widest arm: its 0.12 block half-length ceiling times its 0.10 width ratio ceiling.
     */
    private static final float BASE_WIDEST_ARM = 0.012f;

    @Test
    void twoSeedsDrawShardsThatDifferInCenterAxisAndSpin() {
        ShardTable first = ShardTable.fromSeed(1L);
        ShardTable second = ShardTable.fromSeed(2L);

        int differingCenters = 0;
        int differingAxes = 0;
        int differingSpins = 0;
        for (int shard = 0; shard < ShardTable.MAX_SLIVERS; shard++) {
            differingCenters += first.center(shard).equals(second.center(shard)) ? 0 : 1;
            differingAxes += first.axis(shard).equals(second.axis(shard)) ? 0 : 1;
            differingSpins += first.spinSpeed(shard) == second.spinSpeed(shard) ? 0 : 1;
        }

        assertEquals(ShardTable.MAX_SLIVERS, differingCenters);
        assertEquals(ShardTable.MAX_SLIVERS, differingAxes);
        assertNotEquals(0, differingSpins);
    }

    @Test
    void oneSeedDrawsOneLayout() {
        ShardTable first = ShardTable.fromSeed(3L);
        ShardTable again = ShardTable.fromSeed(3L);

        for (int shard = 0; shard < ShardTable.MAX_SLIVERS; shard++) {
            assertEquals(first.center(shard), again.center(shard));
            assertEquals(first.axis(shard), again.axis(shard));
            assertEquals(first.perp(shard), again.perp(shard));
            assertEquals(first.spinAxis(shard), again.spinAxis(shard));
            assertEquals(first.spinSpeed(shard), again.spinSpeed(shard));
            assertEquals(first.halfLength(shard), again.halfLength(shard));
            assertEquals(first.shape(shard), again.shape(shard));
            assertEquals(first.widthRatio(shard), again.widthRatio(shard));
            assertEquals(first.depthRatio(shard), again.depthRatio(shard));
        }
    }

    @Test
    void everyShardReachesAtLeastTheDoubledFloor() {
        float shortest = Float.MAX_VALUE;
        for (long seed : SIZE_SEEDS) {
            ShardTable shards = ShardTable.fromSeed(seed);
            for (int shard = 0; shard < ShardTable.MAX_SLIVERS; shard++) {
                shortest = Math.min(shortest, shards.halfLength(shard));
            }
        }

        assertTrue(shortest >= DOUBLED_HALF_LENGTH_FLOOR,
                "shortest half-length " + shortest + " under " + DOUBLED_HALF_LENGTH_FLOOR);
    }

    @Test
    void widestShardIsAtLeastAsWideAsTheBaseDrew() {
        float widest = 0f;
        for (long seed : SIZE_SEEDS) {
            ShardTable shards = ShardTable.fromSeed(seed);
            for (int shard = 0; shard < ShardTable.MAX_SLIVERS; shard++) {
                widest = Math.max(widest, shards.widthRatio(shard) * shards.halfLength(shard));
            }
        }

        assertTrue(widest >= BASE_WIDEST_ARM,
                "widest arm " + widest + " under " + BASE_WIDEST_ARM);
    }
}

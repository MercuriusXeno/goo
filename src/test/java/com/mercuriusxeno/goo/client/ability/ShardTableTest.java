package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A cloud's shard layout follows its seed: two seeds draw two layouts, one
 * seed draws one (decision cloud-seed-per-client-ephemeral).
 */
class ShardTableTest {

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
}

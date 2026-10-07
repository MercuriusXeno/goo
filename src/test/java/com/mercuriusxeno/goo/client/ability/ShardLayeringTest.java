package com.mercuriusxeno.goo.client.ability;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Razor's shards appear in layers ahead of the prism dome: a shard is
 * undrawn until the dome reaches nine tenths of its resting distance, born
 * there fully transparent, drifts the last tenth to rest fading in as the
 * dome passes, and sits at rest at full alpha once the dome has passed. Its
 * resting position is fixed by its seed, never scaled by the field's expand.
 * decision razor-shards-appear-in-layers-ahead-of-the-dome
 */
class ShardLayeringTest {

    private static final float RESTING = 4f;
    private static final float TOLERANCE = 1e-5f;
    private static final float REACH = 4.5f;
    /** Ticks into the expand where the dome stands partway out. */
    private static final float EARLY_TICKS = 3f;
    /** Ticks past the dome's growth, where it stands at the cloud's radius. */
    private static final float GROWN_TICKS = 16f;
    private static final ShardTable SHARDS = ShardTable.fromSeed(11L);

    @Test
    void aShardTheDomeHasNotReachedNineTenthsOfIsUndrawn() {
        ShardLayering.ShardReveal reveal = ShardLayering.reveal(RESTING * 0.89f, RESTING);

        assertFalse(reveal.drawn());
    }

    @Test
    void aShardIsBornFullyTransparentAtNineTenthsOfItsRestingDistance() {
        ShardLayering.ShardReveal reveal = ShardLayering.reveal(RESTING * 0.9f, RESTING);

        assertTrue(reveal.drawn());
        assertEquals(RESTING * 0.9f, reveal.distance(), TOLERANCE);
        assertEquals(0f, reveal.alpha(), TOLERANCE);
    }

    @Test
    void aShardTheDomeIsPassingDriftsOutwardAndFadesIn() {
        ShardLayering.ShardReveal nearBirth = ShardLayering.reveal(RESTING * 0.92f, RESTING);
        ShardLayering.ShardReveal nearRest = ShardLayering.reveal(RESTING * 0.98f, RESTING);

        assertTrue(nearBirth.drawn() && nearRest.drawn());
        assertTrue(nearBirth.distance() > RESTING * 0.9f && nearBirth.distance() < nearRest.distance());
        assertTrue(nearRest.distance() < RESTING);
        assertTrue(nearBirth.alpha() > 0f && nearBirth.alpha() < nearRest.alpha());
        assertTrue(nearRest.alpha() < 1f);
    }

    @Test
    void aShardTheDomeHasPassedSitsAtRestAtFullAlpha() {
        ShardLayering.ShardReveal reached = ShardLayering.reveal(RESTING, RESTING);
        ShardLayering.ShardReveal passed = ShardLayering.reveal(RESTING * 1.5f, RESTING);

        assertEquals(RESTING, reached.distance(), TOLERANCE);
        assertEquals(1f, reached.alpha(), TOLERANCE);
        assertEquals(RESTING, passed.distance(), TOLERANCE);
        assertEquals(1f, passed.alpha(), TOLERANCE);
    }

    @Test
    void theDomeGrowsOnTheCrystalBurnoutsShellRadius() {
        float progress = EARLY_TICKS / CrystalExplosionVisual.DURATION_TICKS;

        assertEquals(CrystalExplosionVisual.shellRadius(progress, REACH),
                ShardLayering.domeRadius(EARLY_TICKS, REACH), TOLERANCE);
        assertEquals(REACH, ShardLayering.domeRadius(GROWN_TICKS, REACH), TOLERANCE);
    }

    /**
     * The same seeded layout drawn early in the expand and once the dome has
     * grown: every shard at rest in both stands at one position, its seeded
     * center scaled by the cloud's radius, so no shard scales out from the blob.
     */
    @Test
    void aShardsRestingPositionIsFixedByItsSeedWhateverTheExpand() {
        float earlyDome = ShardLayering.domeRadius(EARLY_TICKS, REACH);
        float grownDome = ShardLayering.domeRadius(GROWN_TICKS, REACH);
        int restingEarly = 0;
        for (int shard = 0; shard < ShardTable.MAX_SLIVERS; shard++) {
            ShardLayering.ShardReveal early = CrystalCloudVisual.revealOf(SHARDS, shard, REACH, earlyDome);
            ShardLayering.ShardReveal grown = CrystalCloudVisual.revealOf(SHARDS, shard, REACH, grownDome);
            Vector3f seeded = SHARDS.center(shard).mul(REACH).add(0.5f, 0.5f, 0.5f);
            Vector3f atGrown = CrystalCloudVisual.shardCenter(SHARDS, shard, grown);

            assertTrue(grown.drawn() && grown.alpha() == 1f, "shard " + shard + " is not at rest once the dome grew");
            assertTrue(seeded.distance(atGrown) < TOLERANCE, "shard " + shard + " rests off its seed");
            if (early.drawn() && early.alpha() == 1f) {
                restingEarly++;
                assertTrue(atGrown.distance(CrystalCloudVisual.shardCenter(SHARDS, shard, early)) < TOLERANCE,
                        "shard " + shard + " moved between the two expands");
            }
        }
        assertTrue(restingEarly > 0 && restingEarly < ShardTable.MAX_SLIVERS,
                "the early dome rests " + restingEarly + " shards, not some of them");
    }
}

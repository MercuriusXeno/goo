package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.world.NetherBehavior;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure tests for the nether accumulator helper. The sphere walk itself is
 * stateful (touches {@code ServerLevel}), but the per-block merge step is
 * extracted as {@link NetherExecutor#mergeValue} so its behavior can be
 * exercised here without spinning up Minecraft's {@code Bootstrap}.
 */
class NetherExecutorTest {

    /**
     * Merging into an empty accumulator plants the value verbatim.
     */
    @Test
    void mergeValue_emptyAccumulator_plantsValue() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        GooValue block = new GooValue(Map.of(GooTypes.ROCK, 1152, GooTypes.NETHER, 500));

        NetherBehavior.mergeValue(totals, block);

        assertEquals(1152, totals.get(GooTypes.ROCK));
        assertEquals(500, totals.get(GooTypes.NETHER));
        assertEquals(2, totals.size());
    }

    /**
     * Merging two blocks with the same type sums their amounts.
     */
    @Test
    void mergeValue_sameType_sums() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        GooValue grass = new GooValue(Map.of(GooTypes.ROCK, 1152));
        GooValue dirt = new GooValue(Map.of(GooTypes.ROCK, 1000));

        NetherBehavior.mergeValue(totals, grass);
        NetherBehavior.mergeValue(totals, dirt);

        assertEquals(2152, totals.get(GooTypes.ROCK));
    }

    /**
     * Merging blocks with different types keeps both, independently.
     */
    @Test
    void mergeValue_differentTypes_independent() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.ROCK, 1000)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.FROST, 500)));

        assertEquals(1000, totals.get(GooTypes.ROCK));
        assertEquals(500, totals.get(GooTypes.FROST));
    }

    /**
     * Many blocks of grass (simulating a nether hit on a grass layer) accumulate cleanly.
     */
    @Test
    void mergeValue_repeatedGrass_accumulates() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        GooValue grass = new GooValue(Map.of(GooTypes.ROCK, 1152));
        int blockCount = 120;

        for (int i = 0; i < blockCount; i++) {
            NetherBehavior.mergeValue(totals, grass);
        }

        assertEquals(1152 * blockCount, totals.get(GooTypes.ROCK));
        // Single entry → nether BE will drop a single omniblob at the center.
        assertEquals(1, totals.size());
    }

    /**
     * Empty GooValue is a no-op on the accumulator.
     */
    @Test
    void mergeValue_empty_noop() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        totals.put(GooTypes.ROCK, 1000);

        NetherBehavior.mergeValue(totals, GooValue.EMPTY);

        assertEquals(1000, totals.get(GooTypes.ROCK));
        assertEquals(1, totals.size());
    }

    /**
     * A block with multiple goo types distributes correctly across the accumulator.
     */
    @Test
    void mergeValue_multiType_distributes() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        GooValue complex = new GooValue(Map.of(
                GooTypes.ROCK, 800,
                GooTypes.BLAZE, 200,
                GooTypes.NETHER, 100));

        NetherBehavior.mergeValue(totals, complex);

        assertEquals(800, totals.get(GooTypes.ROCK));
        assertEquals(200, totals.get(GooTypes.BLAZE));
        assertEquals(100, totals.get(GooTypes.NETHER));
    }

    /**
     * Cross-tick: accumulator survives merges across independent GooValue instances.
     */
    @Test
    void mergeValue_mixedAccumulation_producesCorrectTotals() {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();

        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.ROCK, 1152)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.ROCK, 1000, GooTypes.FROST, 250)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.BLAZE, 500)));
        NetherBehavior.mergeValue(totals, new GooValue(Map.of(GooTypes.FROST, 750)));

        assertEquals(2152, totals.get(GooTypes.ROCK));
        assertEquals(1000, totals.get(GooTypes.FROST));
        assertEquals(500, totals.get(GooTypes.BLAZE));
        assertTrue(totals.get(GooTypes.NETHER) == null || totals.get(GooTypes.NETHER) == 0);
    }
}

package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleMath;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for crucible pure logic: flat extraction rate,
 * proportional drain shares, GooValue-to-GooContents bridge,
 * GooContents operations, and platform movement math.
 */
@ExtendWith(MockitoExtension.class)
class CrucibleBlockEntityTest {

    // -- extractionRate (flat rate, decision melt-rate-is-flat) ------------

    /**
     * The default blaze melt rate answers the same 20 mB/t for a nearly empty pool,
     * a block's worth and a basin near its 2 billion mB cap.
     */
    @Test
    void flatRateIsTheSameAtEveryPoolVolume() {
        int rate = GooConfig.DEFAULT_BLAZE_MELT_RATE;
        assertEquals(20, rate);
        assertEquals(rate, CrucibleMath.extractionRate(1, rate));
        assertEquals(rate, CrucibleMath.extractionRate(1152, rate));
        assertEquals(rate, CrucibleMath.extractionRate(2_000_000_000L, rate));
    }

    /**
     * An empty or negative pool drains nothing.
     */
    @Test
    void emptyPoolDrainsNothing() {
        assertEquals(0, CrucibleMath.extractionRate(0, 20));
        assertEquals(0, CrucibleMath.extractionRate(-100, 20));
    }

    /**
     * A rate below 1 still drains 1 mB/t from a non-empty pool.
     */
    @Test
    void nonPositiveRateDrainsOneMb() {
        assertEquals(1, CrucibleMath.extractionRate(500, 0));
    }

    /**
     * A 1152 mB item drains in 58 ticks at 20 mB/t: 57 full ticks and a 12 mB tail.
     */
    @Test
    void blockOfGooDrainsInFiftyEightTicks() {
        int volume = 1152;
        int ticks = 0;
        while (volume > 0) {
            GooContents pool = new GooContents(Map.of(GooTypes.ROCK, volume));
            int rate = CrucibleMath.extractionRate(volume, GooConfig.DEFAULT_BLAZE_MELT_RATE);
            volume -= CrucibleMath.computeDrainShares(pool, rate).get(GooTypes.ROCK);
            ticks++;
        }
        assertEquals(58, ticks);
    }

    // -- GooValue.toGooContents ------------------------------------------

    /**
     * Empty GooValue produces EMPTY GooContents.
     */
    @Test
    void emptyGooValueProducesEmptyGooContents() {
        GooContents result = GooValue.EMPTY.toGooContents();
        assertTrue(result.isEmpty());
    }

    /**
     * Single-type GooValue uses int correctly.
     */
    @Test
    void singleTypeGooValueConvertsToGooContents() {
        GooValue value = new GooValue(Map.of(GooTypes.ROCK, 500));
        GooContents result = value.toGooContents();
        assertEquals(500, result.getVolume(GooTypes.ROCK));
        assertEquals(1, result.typeCount());
    }

    /**
     * Multi-type GooValue preserves all types with widened amounts.
     */
    @Test
    void multiTypeGooValuePreservesAllTypes() {
        GooValue value = new GooValue(Map.of(
                GooTypes.ROCK, 100,
                GooTypes.METAL, 250,
                GooTypes.VITAL, 50
        ));
        GooContents result = value.toGooContents();
        assertEquals(100, result.getVolume(GooTypes.ROCK));
        assertEquals(250, result.getVolume(GooTypes.METAL));
        assertEquals(50, result.getVolume(GooTypes.VITAL));
        assertEquals(3, result.typeCount());
    }

    // -- GooContents.largestType -----------------------------------------

    /**
     * Empty GooContents has no largest type.
     */
    @Test
    void emptyGooContentsHasNoLargestType() {
        assertNull(GooContents.EMPTY.largestType());
    }

    /**
     * Single-type GooContents returns that type as largest.
     */
    @Test
    void singleTypeGooContentsReturnsThatType() {
        GooContents gc = new GooContents(Map.of(GooTypes.BLAZE, 100));
        assertEquals(GooTypes.BLAZE, gc.largestType());
    }

    /**
     * Multi-type GooContents returns the highest-volume type.
     */
    @Test
    void multiTypeGooContentsReturnsHighestVolume() {
        GooContents gc = new GooContents(Map.of(
                GooTypes.ROCK, 100,
                GooTypes.METAL, 500,
                GooTypes.VITAL, 200
        ));
        assertEquals(GooTypes.METAL, gc.largestType());
    }

    // -- GooContents.mergeWith -------------------------------------------

    /**
     * Merging empty with non-empty returns the non-empty.
     */
    @Test
    void mergeEmptyWithNonEmptyReturnsNonEmpty() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents result = GooContents.EMPTY.mergeWith(gc);
        assertEquals(100, result.getVolume(GooTypes.ROCK));
    }

    /**
     * Merging two contents sums matching types.
     */
    @Test
    void mergeWithSumsMatchingTypes() {
        GooContents a = new GooContents(Map.of(GooTypes.ROCK, 100, GooTypes.METAL, 50));
        GooContents b = new GooContents(Map.of(GooTypes.ROCK, 200, GooTypes.VITAL, 75));
        GooContents result = a.mergeWith(b);
        assertEquals(300, result.getVolume(GooTypes.ROCK));
        assertEquals(50, result.getVolume(GooTypes.METAL));
        assertEquals(75, result.getVolume(GooTypes.VITAL));
    }

    // -- computeDrainShares (proportional distribution) -------------------------

    /**
     * Single type gets the entire rate budget.
     */
    @Test
    void singleTypeDrainShareGetsFullRate() {
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, 1000));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 10);
        assertEquals(10, shares.get(GooTypes.ROCK));
    }

    /**
     * Two equal types split the rate evenly.
     */
    @Test
    void equalTypesSplitEvenly() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 500, GooTypes.METAL, 500));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 10);
        assertEquals(5, shares.get(GooTypes.ROCK));
        assertEquals(5, shares.get(GooTypes.METAL));
    }

    /**
     * Unequal types split proportionally with remainder to largest.
     */
    @Test
    void unequalTypesSplitProportionally() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 750, GooTypes.METAL, 250));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 10);
        // ROCK: floor(10 * 750/1000) = 7, METAL: floor(10 * 250/1000) = 2
        // remainder 1 goes to ROCK (largest) -> 8
        assertEquals(8, shares.get(GooTypes.ROCK));
        assertEquals(2, shares.get(GooTypes.METAL));
    }

    /**
     * Tiny type gets minimum 1 mB per tick.
     */
    @Test
    void tinyTypeGetsMinimumOneMb() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 9999, GooTypes.METAL, 1));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 5);
        // METAL: floor(5 * 1/10000) = 0, clamped to min(1, available=1) = 1
        assertEquals(1, shares.get(GooTypes.METAL));
    }

    /**
     * Share never exceeds available volume for a type.
     */
    @Test
    void shareNeverExceedsAvailable() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 3, GooTypes.METAL, 3));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 100);
        assertEquals(3, shares.get(GooTypes.ROCK));
        assertEquals(3, shares.get(GooTypes.METAL));
    }

    /**
     * Total shares across all types sum to at most the rate budget.
     */
    @Test
    void totalSharesDoNotExceedRate() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 600, GooTypes.METAL, 300, GooTypes.VITAL, 100));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 10);
        int total = shares.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= 10, "Total shares " + total + " should not exceed rate 10");
    }

    /**
     * A 200 mB/t rate against a pool near 2 billion mB split across two types
     * multiplies in long, so the shares stay non-negative and sum to the rate.
     */
    @Test
    void sharesAtTwoBillionPoolSumToRateWithoutOverflow() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 1_500_000_000, GooTypes.METAL, 500_000_000));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 200);
        assertTrue(shares.get(GooTypes.ROCK) >= 0);
        assertTrue(shares.get(GooTypes.METAL) >= 0);
        assertEquals(150, shares.get(GooTypes.ROCK));
        assertEquals(50, shares.get(GooTypes.METAL));
    }

}

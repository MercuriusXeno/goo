package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleMath;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for crucible pure logic: the melt rate ramp,
 * proportional drain shares, GooValue-to-GooContents bridge,
 * GooContents operations, and platform movement math.
 */
@ExtendWith(MockitoExtension.class)
class CrucibleBlockEntityTest {

    // -- extractionRate (ramp on warm goo against cold goo) ------------------

    private static final int ONE_BLOCK_MB = 1152;

    /**
     * Simulates a whole melt through CrucibleMath alone, each tick's drain fed into the warm volume.
     *
     * @param warmVolume the reservoir's volume before the first tick
     * @param coldVolume the item's volume before the first tick
     * @return the rate of each tick, in order
     */
    private static List<Integer> simulateMeltRates(int warmVolume, int coldVolume) {
        List<Integer> rates = new ArrayList<>();
        int warm = warmVolume;
        int cold = coldVolume;
        while (cold > 0) {
            int rate = CrucibleMath.extractionRate(warm, cold);
            int drained = Math.min(rate, cold);
            warm += drained;
            cold -= drained;
            rates.add(rate);
        }
        return rates;
    }

    /**
     * A block melting from an empty reservoir never slows, and its last tick is its fastest.
     */
    @Test
    void wholeMeltFromEmptyRampsWithFastestTail() {
        List<Integer> rates = simulateMeltRates(0, ONE_BLOCK_MB);
        for (int tick = 1; tick < rates.size(); tick++) {
            assertTrue(rates.get(tick) >= rates.get(tick - 1),
                    "rate fell at tick " + tick + ": " + rates.get(tick - 1) + " -> " + rates.get(tick));
        }
        int lastRate = rates.get(rates.size() - 1);
        assertEquals(Collections.max(rates), lastRate);
        assertTrue(lastRate > rates.get(0), "last rate " + lastRate + " should exceed first " + rates.get(0));
    }

    /**
     * Goo already standing in the reservoir melts the next block faster from its first tick.
     */
    @Test
    void warmReservoirMeltsFasterFromFirstTick() {
        int rateFromEmpty = CrucibleMath.extractionRate(0, ONE_BLOCK_MB);
        int rateFromWarm = CrucibleMath.extractionRate(10_000, ONE_BLOCK_MB);
        assertTrue(rateFromWarm > rateFromEmpty,
                "warm first-tick rate " + rateFromWarm + " should exceed empty " + rateFromEmpty);
    }

    /**
     * An item with no cold goo left answers the floor rather than dividing by zero.
     */
    @Test
    void noColdGooYieldsFloorRate() {
        assertEquals(1, CrucibleMath.extractionRate(500, 0));
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

}

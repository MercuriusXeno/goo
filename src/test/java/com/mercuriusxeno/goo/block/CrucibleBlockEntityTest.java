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

    // -- extractionRate (power law of the basin's total goo) -----------------

    private static final int ONE_BLOCK_MB = 1152;
    private static final int STACK_OF_BLOCKS_MB = 64 * ONE_BLOCK_MB;

    /**
     * Simulates a whole melt through CrucibleMath alone, each tick's drain moved from the item to the reservoir.
     *
     * @param reservoirVolume the reservoir's volume before the first tick
     * @param itemVolume      the melting item's volume before the first tick
     * @return the rate of each tick, in order
     */
    private static List<Integer> simulateMeltRates(int reservoirVolume, int itemVolume) {
        List<Integer> rates = new ArrayList<>();
        int reservoir = reservoirVolume;
        int item = itemVolume;
        while (item > 0) {
            int rate = CrucibleMath.extractionRate(reservoir + item);
            int drained = Math.min(rate, item);
            reservoir += drained;
            item -= drained;
            rates.add(rate);
        }
        return rates;
    }

    /**
     * Adding goo to the basin never lowers the rate, across every volume up to a full stack.
     */
    @Test
    void addingGooNeverLowersRate() {
        int previous = CrucibleMath.extractionRate(0);
        for (int total = 1; total <= STACK_OF_BLOCKS_MB; total++) {
            int rate = CrucibleMath.extractionRate(total);
            assertTrue(rate >= previous, "rate fell from " + previous + " to " + rate + " at " + total + " mB");
            previous = rate;
        }
    }

    /**
     * A stack dropped into a basin already holding goo melts at least as fast as it would from empty,
     * and a stack dropped mid-melt raises the rate rather than pulling it to the floor.
     */
    @Test
    void gooAlreadyInBasinNeverSlowsNextItem() {
        int stackFromEmpty = CrucibleMath.extractionRate(STACK_OF_BLOCKS_MB);
        int stackIntoWarm = CrucibleMath.extractionRate(ONE_BLOCK_MB + STACK_OF_BLOCKS_MB);
        assertTrue(stackIntoWarm >= stackFromEmpty);
        assertTrue(simulateMeltRates(ONE_BLOCK_MB, STACK_OF_BLOCKS_MB).size()
                <= simulateMeltRates(0, STACK_OF_BLOCKS_MB).size());

        int midMelt = CrucibleMath.extractionRate(600 + 552);
        int midMeltWithStack = CrucibleMath.extractionRate(600 + 552 + STACK_OF_BLOCKS_MB);
        assertTrue(midMeltWithStack > midMelt,
                "stack dropped mid-melt moved the rate from " + midMelt + " to " + midMeltWithStack);
    }

    /**
     * An empty basin answers the floor, and the floor is well above 1 mB a tick.
     */
    @Test
    void emptyBasinYieldsFloorRate() {
        assertTrue(CrucibleMath.MELT_FLOOR_RATE > 1);
        assertEquals(CrucibleMath.MELT_FLOOR_RATE, CrucibleMath.extractionRate(0));
        assertEquals(CrucibleMath.MELT_FLOOR_RATE, CrucibleMath.extractionRate(-100));
    }

    /**
     * One block melts in about 3 s (60 ticks) from an empty basin.
     * Whole-melt ticks from empty at scale 0.28, exponent 0.6, simulated by simulateMeltRates:
     * 1152 mB (one block) in 61, 18432 mB (16 blocks) in 183, 73728 mB (64 blocks) in 317.
     */
    @Test
    void oneBlockFromEmptyMeltsInAboutThreeSeconds() {
        int ticks = simulateMeltRates(0, ONE_BLOCK_MB).size();
        assertTrue(ticks >= 50 && ticks <= 70, "one block melted in " + ticks + " ticks, expected 50 to 70");
    }

    /**
     * A 64-block stack melts in about 16 s (320 ticks) from an empty basin, not 64 times one block.
     */
    @Test
    void stackFromEmptyMeltsInAboutSixteenSeconds() {
        int ticks = simulateMeltRates(0, STACK_OF_BLOCKS_MB).size();
        assertTrue(ticks >= 280 && ticks <= 360, "a stack melted in " + ticks + " ticks, expected 280 to 360");
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
     * A high rate across large volumes splits proportionally rather than overflowing to 1 mB shares.
     */
    @Test
    void highRateOverLargeVolumesSplitsWithoutOverflow() {
        GooContents pool = new GooContents(Map.of(
                GooTypes.ROCK, 1_000_000, GooTypes.METAL, 1_000_000));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 5000);
        assertEquals(2500, shares.get(GooTypes.ROCK));
        assertEquals(2500, shares.get(GooTypes.METAL));
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

package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure math and logic for the crucible, extracted so unit tests can
 * run without triggering Minecraft class initialization.
 */
public final class CrucibleMath {

    /**
     * The melt rate in mB/tick at zero warm goo.
     */
    static final int MELT_FLOOR_RATE = 1;

    /**
     * The power the warm-to-cold ratio is raised to in the melt ramp.
     */
    static final double MELT_RAMP_EXPONENT = 2.0;

    private CrucibleMath() {
    }

    /**
     * Computes the melt rate (mB/tick) from the warm goo against the cold goo.
     * Formula: max(1, floor(MELT_FLOOR_RATE * (1 + warm / cold) ^ MELT_RAMP_EXPONENT)).
     * The rate rises as warm grows and cold shrinks, so the tail of a melt is its fastest part
     * (melt-rate-ramps-on-warm-goo).
     *
     * @param warmVolume the reservoir's total volume in mB
     * @param coldVolume the melting item's remaining volume in mB
     * @return the melt rate in mB/tick, at least 1
     */
    public static int extractionRate(int warmVolume, int coldVolume) {
        if (coldVolume <= 0) {
            return Math.max(1, MELT_FLOOR_RATE);
        }
        double warmToCold = Math.max(0, warmVolume) / (double) coldVolume;
        double ramp = MELT_FLOOR_RATE * Math.pow(1 + warmToCold, MELT_RAMP_EXPONENT);
        return Math.max(1, (int) Math.floor(ramp));
    }

    /**
     * Moves a value toward a target by at most step, without overshooting.
     *
     * @param current the current value
     * @param target  the target position or property
     * @param step    the step
     * @return the float value
     */
    public static float moveToward(float current, float target, float step) {
        if (current < target) {
            return Math.min(current + step, target);
        }
        return Math.max(current - step, target);
    }

    /**
     * Distributes a total extraction budget proportionally across all goo types
     * in the pool. Each type gets floor(rate * typeVolume / totalVolume), with
     * a minimum of 1 mB (clamped to available volume). Remainder from rounding
     * is given to the largest type.
     *
     * @param contents the goo contents
     * @param rate     the extraction rate in mB/tick
     * @return the computed drain shares
     */
    public static Map<ResourceKey<GooTypeDefinition>, Integer> computeDrainShares(GooContents contents, int rate) {
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = new HashMap<>();
        int totalVolume = contents.totalVolume();
        int allocated = allocateProportional(shares, contents, rate, totalVolume);
        distributeRemainder(shares, contents, rate, allocated);
        return shares;
    }

    /**
     * Allocates each type's share proportionally, floored to at least 1 mB.
     *
     * @param shares      output map for per-type shares
     * @param contents    the goo contents
     * @param rate        the extraction rate
     * @param totalVolume the total goo volume
     * @return the sum of all allocated shares
     */
    private static int allocateProportional(Map<ResourceKey<GooTypeDefinition>, Integer> shares,
                                            GooContents contents, int rate, int totalVolume) {
        int allocated = 0;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : contents.getAll().entrySet()) {
            int available = entry.getValue();
            int share = Math.min(Math.max(1, rate * available / totalVolume), available);
            shares.put(entry.getKey(), share);
            allocated += share;
        }
        return allocated;
    }

    /**
     * Assigns unallocated budget (from rounding) to the largest type,
     * capped at that type's available volume.
     *
     * @param shares    the per-type drain shares
     * @param contents  the goo contents
     * @param rate      the extraction rate in mB/tick
     * @param allocated the total allocated so far
     */
    private static void distributeRemainder(Map<ResourceKey<GooTypeDefinition>, Integer> shares,
                                            GooContents contents, int rate, int allocated) {
        int remainder = rate - allocated;
        if (remainder <= 0) {
            return;
        }
        ResourceKey<GooTypeDefinition> largest = contents.largestType();
        if (largest == null) {
            return;
        }
        int available = contents.getVolume(largest);
        int current = shares.getOrDefault(largest, 0);
        shares.put(largest, Math.min(current + remainder, available));
    }
}

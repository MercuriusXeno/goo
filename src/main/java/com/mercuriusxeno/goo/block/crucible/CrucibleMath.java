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
     * Base exponent for the extraction rate power-law curve.
     */
    static final double BASE_EXPONENT = 0.25;

    private CrucibleMath() {
    }

    /**
     * Computes extraction rate (mB/tick) from remaining pool volume.
     * Formula: max(1, floor(remaining ^ BASE_EXPONENT)).
     * Rate decelerates naturally as the pool drains (half-life feel).
     *
     * @param remaining the remaining volume in mB
     * @return the extraction rate in mB/tick, at least 1
     */
    public static int extractionRate(int remaining) {
        if (remaining <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(Math.pow(remaining, BASE_EXPONENT)));
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

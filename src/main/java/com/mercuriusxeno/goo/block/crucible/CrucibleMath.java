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
     * The least melt rate in mB/tick, for a basin holding little goo.
     */
    public static final int MELT_FLOOR_RATE = 7;

    /**
     * The scale of the melt power law, tuned with MELT_RATE_EXPONENT so one block melts in
     * about 3 s and a 64-block stack in about 16 s (melt-floor-tuned-to-three-seconds).
     */
    static final double MELT_RATE_SCALE = 0.28;

    /**
     * The power the basin's total goo is raised to in the melt rate.
     */
    static final double MELT_RATE_EXPONENT = 0.6;

    private CrucibleMath() {
    }

    /**
     * Computes the melt rate (mB/tick) as a power law of the total goo in the basin,
     * the reservoir plus the melting item, so adding goo never lowers the rate.
     * Formula: max(MELT_FLOOR_RATE, floor(MELT_RATE_SCALE * total ^ MELT_RATE_EXPONENT)).
     *
     * @param basinTotalVolume the reservoir's volume plus the melting item's remaining volume, in mB
     * @return the melt rate in mB/tick, at least MELT_FLOOR_RATE
     */
    public static int extractionRate(int basinTotalVolume) {
        if (basinTotalVolume <= 0) {
            return MELT_FLOOR_RATE;
        }
        double rate = MELT_RATE_SCALE * Math.pow(basinTotalVolume, MELT_RATE_EXPONENT);
        return Math.max(MELT_FLOOR_RATE, (int) Math.floor(rate));
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
            int proportional = (int) ((long) rate * available / totalVolume);
            int share = Math.min(Math.max(1, proportional), available);
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

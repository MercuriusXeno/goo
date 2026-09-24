package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.function.ToIntBiFunction;

/**
 * The crucible's per-type cap and the whole-unit rule every insert follows at
 * it, so nothing offered to a full crucible is destroyed (decision
 * crucible-refuses-past-two-billion).
 */
public final class CrucibleCapacity {

    /** The most one goo type holds in the melt pool, and again in the reservoir. */
    public static final int TYPE_CAPACITY = 2_000_000_000;

    private CrucibleCapacity() {}

    /**
     * Counts the whole units that fit on top of what a store holds, no type
     * passing {@link #TYPE_CAPACITY}.
     *
     * @param held    what the store holds now
     * @param perUnit the goo one unit carries
     * @param offered the units offered
     * @return the units that fit, from zero to {@code offered}
     */
    public static int wholeUnitsThatFit(GooContents held, GooContents perUnit, int offered) {
        long fitting = Math.max(0, offered);
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : perUnit.getAll().entrySet()) {
            long room = (long) TYPE_CAPACITY - held.getVolume(entry.getKey());
            fitting = Math.min(fitting, Math.max(0L, room) / entry.getValue());
        }
        return (int) fitting;
    }

    /**
     * Merges incoming goo into the pool only when every type fits whole.
     *
     * @param pool     the melt pool's contents
     * @param incoming the goo offered to it
     * @return the merged pool, or null when any type would pass the cap
     */
    public static @Nullable GooContents mergedWithinCap(GooContents pool, GooContents incoming) {
        if (wholeUnitsThatFit(pool, incoming, 1) < 1) {
            return null;
        }
        return pool.mergeWith(incoming);
    }

    /**
     * Drains each type's share from the pool into the reservoir, removing from
     * the pool only what the reservoir took, so a full type stays in the pool.
     *
     * @param pool            the melt pool's contents
     * @param shares          the per-type drain amounts, each within the pool's volume
     * @param reservoirInsert inserts a type's amount into the reservoir, answering what it took
     * @return the pool after the drain
     */
    public static GooContents drainAccepted(GooContents pool, Map<ResourceKey<GooTypeDefinition>, Integer> shares,
                                            ToIntBiFunction<ResourceKey<GooTypeDefinition>, Integer> reservoirInsert) {
        GooContents remaining = pool;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : shares.entrySet()) {
            int taken = reservoirInsert.applyAsInt(entry.getKey(), entry.getValue());
            remaining = remaining.withRemoved(entry.getKey(), taken);
        }
        return remaining;
    }
}

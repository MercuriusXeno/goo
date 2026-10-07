package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;

/**
 * How long an unmake takes and what it leaves: a block dissolves after work
 * proportional to what the crucible would charge to melt it, and yields a
 * share of the goo the crucible would.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeRule {

    private UnmakeRule() {
    }

    /**
     * The work a block takes to unmake: its whole goo value times the work
     * each mB costs, never less than one.
     *
     * @param totalGoo   the block's goo value summed over types, in mB
     * @param workPerGoo the work one mB costs, the ability's efficiency divisor
     * @return the work, in held ticks or drips
     */
    public static int workToUnmake(int totalGoo, double workPerGoo) {
        return Math.max(1, (int) Math.ceil(totalGoo * workPerGoo));
    }

    /**
     * The goo an unmade block leaves: each type's amount times the yield,
     * rounded down, a type left with none dropped.
     *
     * @param value      the block's goo value
     * @param efficiency the share of the value the unmake keeps, 0 to 1
     * @return the goo left behind
     */
    public static GooContents yieldOf(GooValue value, double efficiency) {
        Map<ResourceKey<GooTypeDefinition>, Integer> kept = new HashMap<>();
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : value.getAll().entrySet()) {
            int amount = (int) Math.floor(entry.getValue() * efficiency);
            if (amount > 0) {
                kept.put(entry.getKey(), amount);
            }
        }
        return new GooContents(kept);
    }
}

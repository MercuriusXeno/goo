package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * What the crucible would make of a mob: the loot it would drop, rolled once
 * from its loot table as a kill by magic would roll it, as the goo of every
 * stack and the value of the dearest stack, which melts slowest since each
 * stack melts as one unit. Unmake leaves that goo in place of the items.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeLoot {

    /**
     * A mob's loot as the crucible would melt it.
     *
     * @param goo         the goo of every stack together
     * @param slowestUnit the goo value of the dearest stack whole, in mB
     */
    public record Loot(GooValue goo, long slowestUnit) {
    }

    private UnmakeLoot() {
    }

    /**
     * Rolls the mob's loot once and weighs every stack.
     *
     * @param level the server level
     * @param mob   the mob
     * @return the loot, or null when it would drop nothing of value
     */
    public static @Nullable Loot lootOf(ServerLevel level, LivingEntity mob) {
        IGooValueLookup values = GooValues.of(level);
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        long[] slowest = {0};
        mob.getLootTable().ifPresent(table -> mob.dropFromLootTable(level, mob.damageSources().magic(), true, table,
                stack -> slowest[0] = Math.max(slowest[0], addStack(values, stack, totals))));
        GooValue goo = new GooValue(totals);
        return goo.isEmpty() ? null : new Loot(goo, slowest[0]);
    }

    /**
     * Adds one rolled stack's goo value, times its count, to the totals.
     *
     * @param values the goo value lookup
     * @param stack  the rolled stack
     * @param totals the totals to add into
     * @return the stack's whole goo value, in mB
     */
    private static long addStack(IGooValueLookup values, ItemStack stack,
                                 Map<ResourceKey<GooTypeDefinition>, Integer> totals) {
        GooValue value = values.lookup(stack);
        if (value == null) {
            return 0;
        }
        value.getAll().forEach((type, amount) -> totals.merge(type, amount * stack.getCount(), Integer::sum));
        return (long) value.totalGoo() * stack.getCount();
    }
}

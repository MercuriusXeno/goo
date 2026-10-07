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
 * What the crucible would make of a mob: the summed goo value of the loot it
 * would drop, rolled once from its loot table as a kill by magic would roll
 * it. Unmake works a mob for that value's crucible cost and leaves that goo
 * in place of the items.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeLoot {

    private UnmakeLoot() {
    }

    /**
     * Rolls the mob's loot once and sums the goo value of every stack.
     *
     * @param level the server level
     * @param mob   the mob
     * @return the loot's goo value, or null when it would drop nothing of value
     */
    public static @Nullable GooValue valueOf(ServerLevel level, LivingEntity mob) {
        IGooValueLookup values = GooValues.of(level);
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        mob.getLootTable().ifPresent(table -> mob.dropFromLootTable(level, mob.damageSources().magic(), true, table,
                stack -> addStack(values, stack, totals)));
        GooValue value = new GooValue(totals);
        return value.isEmpty() ? null : value;
    }

    /**
     * Adds one rolled stack's goo value, times its count, to the totals.
     *
     * @param values the goo value lookup
     * @param stack  the rolled stack
     * @param totals the totals to add into
     */
    private static void addStack(IGooValueLookup values, ItemStack stack,
                                 Map<ResourceKey<GooTypeDefinition>, Integer> totals) {
        GooValue value = values.lookup(stack);
        if (value == null) {
            return;
        }
        value.getAll().forEach((type, amount) -> totals.merge(type, amount * stack.getCount(), Integer::sum));
    }
}

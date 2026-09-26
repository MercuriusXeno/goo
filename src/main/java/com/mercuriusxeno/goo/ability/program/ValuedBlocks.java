package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Map;

/**
 * The world side of {@link ConsumedGooHost#consumeValuedBlocks}: walks a sphere
 * of blocks, and each block whose item has a goo value adds its whole
 * value to the total and is removed; every other block stands.
 */
public final class ValuedBlocks {

    private ValuedBlocks() {
    }

    /**
     * Consumes the valued blocks within a sphere, leaving its center.
     *
     * @param level  the level to consume from
     * @param center the sphere center, which the walk skips
     * @param radius the sphere radius in whole blocks
     * @return the goo the consumed blocks held
     */
    static GooContents consumeSphere(ServerLevel level, BlockPos center, int radius) {
        Map<ResourceKey<GooTypeDefinition>, Integer> totals = new HashMap<>();
        AbilityMath.forEachInSphere(center, radius, target -> {
            if (!target.equals(center)) {
                consumeIfValued(level, target, totals);
            }
        });
        return new GooContents(totals);
    }

    /**
     * Removes the block at the position when its item has a goo value,
     * merging the value into the totals.
     *
     * @param level  the level
     * @param target the block position
     * @param totals the totals to merge into
     */
    private static void consumeIfValued(ServerLevel level, BlockPos target,
                                        Map<ResourceKey<GooTypeDefinition>, Integer> totals) {
        BlockState state = level.getBlockState(target);
        Item item = state.getBlock().asItem();
        if (state.isAir() || item == Items.AIR) {
            return;
        }
        GooValue value = Goo.GOO_VALUES.lookup(BuiltInRegistries.ITEM.getKey(item));
        if (value == null || value.isEmpty()) {
            return;
        }
        mergeValue(totals, value);
        level.removeBlock(target, false);
    }

    /**
     * Merges a goo value's per-type amounts into a totals map, summing
     * amounts of a type already present.
     *
     * @param totals the totals to merge into
     * @param value  the goo value to add
     */
    public static void mergeValue(Map<ResourceKey<GooTypeDefinition>, Integer> totals, GooValue value) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : value.getAll().entrySet()) {
            totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }
}

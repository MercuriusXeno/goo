package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The goo a standing block is worth to the abilities that weigh it: its
 * item's goo value, what the crucible would melt it into, which a
 * Gouge lift reads to pick the blocks it can tear free.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class ValuedBlocks {

    private ValuedBlocks() {
    }

    /**
     * The goo the block at a position holds: its item's goo value, what the
     * crucible would melt it into.
     *
     * @param level  the level
     * @param target the block position
     * @return the block's goo value, or null for air, an itemless block or an unvalued one
     */
    public static @Nullable GooValue valueAt(ServerLevel level, BlockPos target) {
        BlockState state = level.getBlockState(target);
        Item item = state.getBlock().asItem();
        if (state.isAir() || item == Items.AIR) {
            return null;
        }
        return GooValues.of(level).lookup(BuiltInRegistries.ITEM.getKey(item));
    }
}

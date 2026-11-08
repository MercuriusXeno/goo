package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A block cooking toward its furnace result under a held flame: every block
 * heats up the same way and finishes the same way, becoming its result where
 * that is a block, or emptying with its result item left at rest in its cell
 * (decision blocks-that-cook-into-items-cook-like-blocks).
 */
final class BlockCooking {

    private BlockCooking() {
    }

    /**
     * The furnace result of each item under a level's live recipes, each
     * item's answer looked up once for the tick.
     *
     * @param level the server level
     * @return the result an item smelts into, empty where no recipe smelts it
     */
    static Function<Item, Optional<ItemStack>> resultsIn(ServerLevel level) {
        Map<Item, Optional<ItemStack>> results = new HashMap<>();
        return raw -> results.computeIfAbsent(raw, item -> smelted(level, item));
    }

    /**
     * The block a block cooks into: its item's furnace result where that
     * result places a block other than the one it came from, or the empty
     * block it leaves where the result is an item that places none.
     *
     * @param raw     the block standing in the heat
     * @param smelt   the furnace result of an item, empty where no recipe smelts it
     * @param emptied the block left where the raw block cooks into an item, air in play
     * @return the cooked block, or empty where the block does not cook
     */
    static Optional<Block> cookedBlock(Block raw, Function<Item, Optional<Item>> smelt, Block emptied) {
        return smelt.apply(raw.asItem())
                .map(result -> result instanceof BlockItem placed ? placed.getBlock() : emptied)
                .filter(block -> block != raw);
    }

    /**
     * Finishes a cooked block, the one finish every cooked block takes: it
     * becomes its result where that is a block, or empties with the result
     * item resting at its center.
     *
     * @param host    the host the block cooks under
     * @param pos     the block
     * @param result  its furnace result
     * @param emptied the state left where the result is an item, air in play
     */
    static void finishCooked(BlockBreakHost host, BlockPos pos, ItemStack result, BlockState emptied) {
        if (result.getItem() instanceof BlockItem placed) {
            host.transformBlock(pos, placed.getBlock().defaultBlockState());
        } else {
            host.transformBlock(pos, emptied);
            host.restItem(Vec3.atCenterOf(pos), result);
        }
    }

    /**
     * An item's smelting result under the server's live recipes.
     *
     * @param level the server level
     * @param item  the item a furnace would take
     * @return the result, or empty where no smelting recipe takes it
     */
    private static Optional<ItemStack> smelted(ServerLevel level, Item item) {
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(item));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                .map(holder -> holder.value().assemble(input))
                .filter(result -> !result.isEmpty());
    }
}

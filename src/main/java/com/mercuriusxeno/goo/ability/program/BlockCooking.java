package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A block cooking toward its furnace result under a held flame or a Molten
 * cell: every block heats up the same way and finishes the same way, becoming
 * its result where that is a block, or emptying with its result item left at
 * rest in its cell (decision blocks-that-cook-into-items-cook-like-blocks).
 * Flames and Molten read the one heated-into rule here, so both heat a block
 * into the same block.
 * molten-cooks-the-block-below-it-as-flames-cooks-blocks
 */
public final class BlockCooking {

    private BlockCooking() {
    }

    /**
     * The furnace result of each item under a level's live recipes, each
     * item's answer looked up once for the tick.
     *
     * @param level the server level
     * @return the result an item smelts into, empty where no recipe smelts it
     */
    public static Function<Item, Optional<ItemStack>> resultsIn(ServerLevel level) {
        Map<Item, Optional<ItemStack>> results = new HashMap<>();
        return raw -> results.computeIfAbsent(raw, item -> smelted(level, item));
    }

    /**
     * Whether water evaporates at a position, the ultrawarm rule a melt of
     * ice reads.
     *
     * @param level the server level
     * @param at    the position
     * @return true where water evaporates there
     */
    public static boolean waterEvaporatesAt(ServerLevel level, BlockPos at) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, at);
    }

    /**
     * The block a block in the heat becomes under a level's live recipes,
     * vanilla's meltables and the evaporation at a position.
     *
     * @param level   the server level
     * @param results the furnace result of an item, empty where no recipe smelts it
     * @param at      the position whose evaporation a melt of ice reads
     * @return the block each block becomes, empty where it neither melts nor cooks
     */
    public static Function<Block, Optional<Block>> heatedIntoAt(ServerLevel level,
                                                                Function<Item, Optional<ItemStack>> results,
                                                                BlockPos at) {
        return heatedInto(Meltables.vanilla(), waterEvaporatesAt(level, at),
                item -> results.apply(item).map(ItemStack::getItem), Blocks.AIR);
    }

    /**
     * The block a block in the heat becomes: its melt where it melts, its
     * furnace result otherwise.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     * molten-cooks-the-block-below-it-as-flames-cooks-blocks
     *
     * @param meltables       the blocks that melt and what they melt into
     * @param waterEvaporates whether the dimension evaporates water
     * @param smelt           the furnace result of an item, empty where no recipe smelts it
     * @param emptied         the block left where a block cooks into an item, air in play
     * @return the block each block becomes, empty where it neither melts nor cooks
     */
    static Function<Block, Optional<Block>> heatedInto(Meltables meltables, boolean waterEvaporates,
                                                       Function<Item, Optional<Item>> smelt, Block emptied) {
        return raw -> meltedBlock(raw, meltables, waterEvaporates).or(() -> cookedBlock(raw, smelt, emptied));
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
     * The blocks a flame melts and the blocks they melt into, vanilla's in play.
     *
     * @param ice        ice
     * @param frostedIce frosted ice
     * @param snowLayer  a snow layer
     * @param water      the water source ice melts into
     * @param air        what a snow layer, or ice where water evaporates, leaves
     */
    record Meltables(Block ice, Block frostedIce, Block snowLayer, Block water, Block air) {

        /**
         * Vanilla's meltables, the ones in play, built on each call so a unit test building its own loads no block.
         *
         * @return vanilla's ice, frosted ice, snow layer, water and air
         */
        static Meltables vanilla() {
            return new Meltables(Blocks.ICE, Blocks.FROSTED_ICE, Blocks.SNOW, Blocks.WATER, Blocks.AIR);
        }
    }

    /**
     * The block a block melts into under the heat, the rule vanilla's
     * IceBlock.melt runs: ice and frosted ice leave a water source, or air
     * where the dimension evaporates water, and a snow layer leaves air.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param raw             the block standing in the heat
     * @param meltables       the blocks that melt and what they melt into
     * @param waterEvaporates whether the dimension evaporates water, the ultrawarm rule
     * @return the melted block, or empty where the block does not melt
     */
    static Optional<Block> meltedBlock(Block raw, Meltables meltables, boolean waterEvaporates) {
        if (raw == meltables.ice() || raw == meltables.frostedIce()) {
            return Optional.of(waterEvaporates ? meltables.air() : meltables.water());
        }
        return raw == meltables.snowLayer() ? Optional.of(meltables.air()) : Optional.empty();
    }

    /**
     * Finishes a heated block under a level's live recipes and vanilla's
     * meltables: a melting block becomes its melt with no item rested, and
     * any other takes the one cooking finish.
     * molten-cooks-the-block-below-it-as-flames-cooks-blocks
     *
     * @param host    the host the block heats under
     * @param pos     the block
     * @param raw     the block heated there
     * @param into    the block it becomes
     * @param results the furnace result of an item, empty where no recipe smelts it
     */
    public static void finishHeatedAt(BlockFinishHost host, BlockPos pos, Block raw, Block into,
                                      Function<Item, Optional<ItemStack>> results) {
        boolean evaporates = waterEvaporatesAt(host.level(), pos);
        Meltables vanilla = Meltables.vanilla();
        finishHeated(host, pos, raw, into, block -> meltedBlock(block, vanilla, evaporates), results,
                Blocks.AIR.defaultBlockState());
    }

    /**
     * Finishes a heated block: a melting block becomes its melt with no item
     * rested, and any other takes the one cooking finish.
     * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
     *
     * @param host    the host the block heats under
     * @param pos     the block
     * @param raw     the block heated there
     * @param into    the block it becomes
     * @param melts   the block a block melts into, empty where it does not melt
     * @param results the furnace result of an item, empty where no recipe smelts it
     * @param emptied the state left where a block cooks into an item, air in play
     */
    static void finishHeated(BlockFinishHost host, BlockPos pos, Block raw, Block into,
                             Function<Block, Optional<Block>> melts, Function<Item, Optional<ItemStack>> results,
                             BlockState emptied) {
        if (melts.apply(raw).isPresent()) {
            host.transformBlock(pos, into.defaultBlockState());
        } else {
            results.apply(raw.asItem()).ifPresent(result -> finishCooked(host, pos, result.copy(), emptied));
        }
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
    static void finishCooked(BlockFinishHost host, BlockPos pos, ItemStack result, BlockState emptied) {
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

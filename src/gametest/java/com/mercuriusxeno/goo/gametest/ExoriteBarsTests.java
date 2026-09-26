package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collections;
import java.util.List;

/**
 * Gametests for exorite bars (decision exorite-bars-retextured-iron-bars):
 * the block is iron bars under an exorite name, six exorite craft sixteen,
 * it is as hard and blast resistant as obsidian and drops only to a diamond
 * pickaxe or better, and breaking it drops itself.
 */
public final class ExoriteBarsTests {

    private static final BlockPos BARS_POS = new BlockPos(1, 1, 1);
    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 2;
    private static final int CRAFTED_COUNT = 16;
    private static final float OBSIDIAN_HARDNESS = 50.0F;
    private static final float OBSIDIAN_BLAST_RESISTANCE = 1200.0F;
    private static final double DROP_REACH = 2.0;

    private static final String NOT_BARS_BLOCK = "goo:exorite_bars should be an IronBarsBlock";
    private static final String NOT_BLOCK_ITEM = "goo:exorite_bars should have its BlockItem";
    private static final String NOT_IN_TAB = "The goo creative tab should show exorite bars";
    private static final String NOT_CRAFTED = "Six exorite in two rows of three should craft sixteen exorite bars";
    private static final String IRON_CRAFTS_GOO = "Six iron ingots should not craft exorite bars";
    private static final String WRONG_HARDNESS = "Exorite bars should have the hardness of obsidian";
    private static final String WRONG_BLAST_RESISTANCE = "Exorite bars should have the blast resistance of obsidian";
    private static final String IRON_HARVESTS = "An iron pickaxe should not harvest exorite bars";
    private static final String DIAMOND_CANNOT_HARVEST = "A diamond pickaxe should harvest exorite bars";
    private static final String WRONG_DROP =
            "Breaking exorite bars with a diamond pickaxe should drop one exorite bars, but dropped ";

    private ExoriteBarsTests() {
    }

    /**
     * The block registry holds exorite bars as an IronBarsBlock, the item
     * registry holds its BlockItem, and the goo creative tab shows it.
     *
     * @param helper the gametest helper
     */
    public static void registered(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.EXORITE_BARS.getId());
        helper.assertTrue(block instanceof IronBarsBlock && block == GooBlocks.EXORITE_BARS.get(), NOT_BARS_BLOCK);
        helper.assertTrue(BuiltInRegistries.ITEM.getValue(GooItems.EXORITE_BARS.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_BLOCK_ITEM);
        CreativeModeTab tab = GooCreativeTabs.GOO_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        helper.assertTrue(tab.getDisplayItems().stream().anyMatch(stack -> stack.is(GooItems.EXORITE_BARS.get())),
                NOT_IN_TAB);
        helper.succeed();
    }

    /**
     * Six exorite in two rows of three craft sixteen exorite bars; six iron
     * ingots in the same grid do not craft exorite bars.
     *
     * @param helper the gametest helper
     */
    public static void crafted(GameTestHelper helper) {
        ItemStack bars = craftRows(helper, new ItemStack(GooItems.EXORITE.get()));
        helper.assertTrue(bars.is(GooItems.EXORITE_BARS.get()) && bars.getCount() == CRAFTED_COUNT, NOT_CRAFTED);
        ItemStack fromIron = craftRows(helper, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(!fromIron.is(GooItems.EXORITE_BARS.get()), IRON_CRAFTS_GOO);
        helper.succeed();
    }

    /**
     * Placed exorite bars read the hardness and blast resistance of obsidian,
     * and of iron and diamond pickaxes only the diamond one harvests them.
     *
     * @param helper the gametest helper
     */
    public static void strength(GameTestHelper helper) {
        helper.setBlock(BARS_POS, GooBlocks.EXORITE_BARS.get());
        BlockState state = helper.getBlockState(BARS_POS);
        BlockPos pos = helper.absolutePos(BARS_POS);
        helper.assertTrue(state.getDestroySpeed(helper.getLevel(), pos) == OBSIDIAN_HARDNESS, WRONG_HARDNESS);
        // No explosion: IronBarsBlock reads its resistance from its properties alone.
        helper.assertTrue(state.getExplosionResistance(helper.getLevel(), pos, null) == OBSIDIAN_BLAST_RESISTANCE,
                WRONG_BLAST_RESISTANCE);
        helper.assertTrue(!new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state), IRON_HARVESTS);
        helper.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), DIAMOND_CANNOT_HARVEST);
        helper.succeed();
    }

    /**
     * Exorite bars broken with a diamond pickaxe drop one exorite bars item.
     *
     * @param helper the gametest helper
     */
    public static void dropsItself(GameTestHelper helper) {
        helper.setBlock(BARS_POS, GooBlocks.EXORITE_BARS.get());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BARS_POS);
        Block.dropResources(level.getBlockState(pos), level, pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE));
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BARS_POS, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.EXORITE_BARS.get())
                && drops.getFirst().getItem().getCount() == 1,
                WRONG_DROP + drops.stream().map(ItemEntity::getItem).toList());
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    private static ItemStack craftRows(GameTestHelper helper, ItemStack ingredient) {
        CraftingInput grid = CraftingInput.of(GRID_WIDTH, GRID_HEIGHT,
                Collections.nCopies(GRID_WIDTH * GRID_HEIGHT, ingredient));
        ServerLevel level = helper.getLevel();
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
    }
}

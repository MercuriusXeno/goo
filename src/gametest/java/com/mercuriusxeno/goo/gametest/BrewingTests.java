package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.BlobStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Gametests for goo on the vanilla brewing stand: an omniblob is no brew
 * ingredient (decision new-thread-swaps-the-ingredient). Each case sets a
 * real stand and lets vanilla tick it past its brew time.
 */
public final class BrewingTests {

    /** Vanilla's brew time, 400 ticks, plus margin for the stand to pick the brew up. */
    static final int BREW_WAIT_TICKS = 420;

    private static final int BOTTLE_SLOTS = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final BlockPos GOO_STAND = new BlockPos(1, 1, 1);
    private static final BlockPos CONTROL_STAND = new BlockPos(3, 1, 1);

    private BrewingTests() {
    }

    /**
     * An omniblob of a type over three awkward potions never brews: after the
     * brew time the bottles still hold awkward potion and the omniblob is still
     * in the slot. A nether wart over water on a second stand brews awkward in
     * the same time, so the stand was ticking.
     *
     * @param helper the gametest helper
     */
    public static void omniblobNeverBrews(GameTestHelper helper) {
        ItemStack omniblob = BlobStacks.createForOutput(GooTypes.ENDER, BlobStacks.MB_PER_BLOB);
        BrewingStandBlockEntity gooStand = placeStand(helper, GOO_STAND, Potions.AWKWARD, omniblob);
        BrewingStandBlockEntity controlStand = placeStand(helper, CONTROL_STAND, Potions.WATER,
                new ItemStack(Items.NETHER_WART));
        helper.runAfterDelay(BREW_WAIT_TICKS, () -> {
            assertBottlesHold(helper, controlStand, Potions.AWKWARD, "the nether wart control");
            assertBottlesHold(helper, gooStand, Potions.AWKWARD, "the omniblob stand");
            helper.assertTrue(ItemStack.matches(omniblob, gooStand.getItem(INGREDIENT_SLOT)),
                    "The omniblob should stay in the ingredient slot, found " + gooStand.getItem(INGREDIENT_SLOT));
            helper.succeed();
        });
    }

    /**
     * Places a fuelled brewing stand holding three bottles of the base potion
     * and the ingredient.
     *
     * @param helper     the gametest helper
     * @param pos        the stand's relative position
     * @param base       the potion in each bottle
     * @param ingredient the stack for the ingredient slot, copied in
     * @return the stand's block entity
     */
    static BrewingStandBlockEntity placeStand(GameTestHelper helper, BlockPos pos, Holder<Potion> base,
                                              ItemStack ingredient) {
        helper.setBlock(pos, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(pos, BrewingStandBlockEntity.class);
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            stand.setItem(slot, PotionContents.createItemStack(Items.POTION, base));
        }
        stand.setItem(INGREDIENT_SLOT, ingredient.copy());
        stand.setItem(FUEL_SLOT, new ItemStack(Items.BLAZE_POWDER));
        return stand;
    }

    /**
     * Asserts each bottle slot holds the container with the expected potion.
     *
     * @param helper   the gametest helper
     * @param stand    the stand read
     * @param expected the potion each bottle should hold
     * @param label    names the stand in a failure
     */
    static void assertBottlesHold(GameTestHelper helper, BrewingStandBlockEntity stand,
                                  Holder<Potion> expected, String label) {
        assertBottlesHold(helper, stand, Items.POTION, expected, label);
    }

    /**
     * Asserts each bottle slot holds the container with the expected potion.
     *
     * @param helper    the gametest helper
     * @param stand     the stand read
     * @param container the bottle item expected
     * @param expected  the potion each bottle should hold
     * @param label     names the stand in a failure
     */
    static void assertBottlesHold(GameTestHelper helper, BrewingStandBlockEntity stand, Item container,
                                  Holder<Potion> expected, String label) {
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            ItemStack bottle = stand.getItem(slot);
            PotionContents contents = bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            helper.assertTrue(bottle.is(container) && contents.is(expected),
                    "Bottle " + slot + " of " + label + " should hold " + expected.getRegisteredName()
                            + ", found " + bottle + " " + contents);
        }
    }
}

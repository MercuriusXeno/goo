package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooPotions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
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
 * ingredient (decision new-thread-swaps-the-ingredient), and one chrysm of a
 * type brews that type's potion (decision brew-from-chrysm-on-vanilla-stand).
 * Each case sets a real stand and lets vanilla tick it past its brew time.
 */
public final class BrewingTests {

    /** Vanilla's brew time, 400 ticks, plus margin for the stand to pick the brew up. */
    private static final int BREW_WAIT_TICKS = 420;

    private static final int BOTTLE_SLOTS = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int CHRYSM_STACK = 3;
    private static final BlockPos FIRST_STAND = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND_STAND = new BlockPos(3, 1, 1);
    private static final ResourceKey<GooTypeDefinition> TYPE = GooTypes.ENDER;
    private static final String INGREDIENT_KEPT = " should stay in the ingredient slot, found ";

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
        ItemStack omniblob = BlobStacks.createForOutput(TYPE, BlobStacks.MB_PER_BLOB);
        BrewingStandBlockEntity gooStand = placeStand(helper, FIRST_STAND, Items.POTION, Potions.AWKWARD, omniblob);
        BrewingStandBlockEntity controlStand = placeStand(helper, SECOND_STAND, Items.POTION, Potions.WATER,
                new ItemStack(Items.NETHER_WART));
        helper.runAfterDelay(BREW_WAIT_TICKS, () -> {
            assertBottlesHold(helper, controlStand, Items.POTION, Potions.AWKWARD, "the nether wart control");
            assertBottlesHold(helper, gooStand, Items.POTION, Potions.AWKWARD, "the omniblob stand");
            helper.assertTrue(ItemStack.matches(omniblob, gooStand.getItem(INGREDIENT_SLOT)),
                    "The omniblob" + INGREDIENT_KEPT + gooStand.getItem(INGREDIENT_SLOT));
            helper.succeed();
        });
    }

    /**
     * A stack of three chrysm of a type over three awkward potions brews three
     * potions of that type and leaves two chrysm: one brew spends one chrysm.
     *
     * @param helper the gametest helper
     */
    public static void chrysmBrewsTypePotion(GameTestHelper helper) {
        chrysmBrewsIn(helper, Items.POTION);
    }

    /**
     * One chrysm of a type brews that type's splash potion over awkward splash potions.
     *
     * @param helper the gametest helper
     */
    public static void chrysmBrewsTypeSplashPotion(GameTestHelper helper) {
        chrysmBrewsIn(helper, Items.SPLASH_POTION);
    }

    /**
     * One chrysm of a type brews that type's lingering potion over awkward lingering potions.
     *
     * @param helper the gametest helper
     */
    public static void chrysmBrewsTypeLingeringPotion(GameTestHelper helper) {
        chrysmBrewsIn(helper, Items.LINGERING_POTION);
    }

    /**
     * A kilochrysm and a megachrysm, each on its own stand over awkward
     * potions, never brew: the bottles stay awkward and the item stays.
     *
     * @param helper the gametest helper
     */
    public static void higherTiersNeverBrew(GameTestHelper helper) {
        ItemStack kilochrysm = GooItems.KILOCHRYSM.get().createOf(TYPE);
        ItemStack megachrysm = GooItems.MEGACHRYSM.get().createOf(TYPE);
        BrewingStandBlockEntity kiloStand = placeStand(helper, FIRST_STAND, Items.POTION, Potions.AWKWARD, kilochrysm);
        BrewingStandBlockEntity megaStand = placeStand(helper, SECOND_STAND, Items.POTION, Potions.AWKWARD, megachrysm);
        helper.runAfterDelay(BREW_WAIT_TICKS, () -> {
            assertBottlesHold(helper, kiloStand, Items.POTION, Potions.AWKWARD, "the kilochrysm stand");
            assertBottlesHold(helper, megaStand, Items.POTION, Potions.AWKWARD, "the megachrysm stand");
            helper.assertTrue(ItemStack.matches(kilochrysm, kiloStand.getItem(INGREDIENT_SLOT)),
                    "The kilochrysm" + INGREDIENT_KEPT + kiloStand.getItem(INGREDIENT_SLOT));
            helper.assertTrue(ItemStack.matches(megachrysm, megaStand.getItem(INGREDIENT_SLOT)),
                    "The megachrysm" + INGREDIENT_KEPT + megaStand.getItem(INGREDIENT_SLOT));
            helper.succeed();
        });
    }

    private static void chrysmBrewsIn(GameTestHelper helper, Item container) {
        ItemStack chrysm = GooItems.CHRYSM.get().createOf(TYPE).copyWithCount(CHRYSM_STACK);
        BrewingStandBlockEntity stand = placeStand(helper, FIRST_STAND, container, Potions.AWKWARD, chrysm);
        helper.runAfterDelay(BREW_WAIT_TICKS, () -> {
            assertBottlesHold(helper, stand, container, GooPotions.GOO_POTIONS.get(TYPE), "the chrysm stand");
            ItemStack left = stand.getItem(INGREDIENT_SLOT);
            helper.assertTrue(ItemStack.isSameItemSameComponents(left, chrysm) && left.getCount() == CHRYSM_STACK - 1,
                    "One brew should spend one chrysm of " + CHRYSM_STACK + ", found " + left);
            helper.succeed();
        });
    }

    /**
     * Places a fuelled brewing stand holding three bottles of the base potion
     * and the ingredient.
     *
     * @param helper     the gametest helper
     * @param pos        the stand's relative position
     * @param container  the bottle item
     * @param base       the potion in each bottle
     * @param ingredient the stack for the ingredient slot, copied in
     * @return the stand's block entity
     */
    private static BrewingStandBlockEntity placeStand(GameTestHelper helper, BlockPos pos, Item container,
                                                      Holder<Potion> base, ItemStack ingredient) {
        helper.setBlock(pos, Blocks.BREWING_STAND);
        BrewingStandBlockEntity stand = helper.getBlockEntity(pos, BrewingStandBlockEntity.class);
        for (int slot = 0; slot < BOTTLE_SLOTS; slot++) {
            stand.setItem(slot, PotionContents.createItemStack(container, base));
        }
        stand.setItem(INGREDIENT_SLOT, ingredient.copy());
        stand.setItem(FUEL_SLOT, new ItemStack(Items.BLAZE_POWDER));
        return stand;
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
    private static void assertBottlesHold(GameTestHelper helper, BrewingStandBlockEntity stand, Item container,
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

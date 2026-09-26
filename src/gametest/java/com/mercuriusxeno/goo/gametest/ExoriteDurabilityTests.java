package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.item.ExoriteGear;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

/**
 * Gametests for the exorite durability floor (decision
 * zero-durability-stops-working): damage past max leaves a piece in hand at 0
 * durability, a broken piece works as a bare hand and says so in its
 * tooltip, and exorite on an anvil makes it work again.
 */
public final class ExoriteDurabilityTests {

    private static final String REMOVAL = "removal";
    private static final int OVERKILL_DAMAGE = 10;
    private static final int ANVIL_LEFT = 0;
    private static final int ANVIL_RIGHT = 1;
    private static final int ANVIL_RESULT = 2;
    private static final int MENU_ID = 0;

    private static final String PICKAXE_LOST = "The exorite pickaxe should stay in the main hand past max damage";
    private static final String NOT_AT_FLOOR = "The exorite pickaxe should sit at max damage";
    private static final String CREATIVE_DAMAGED = "A creative player's exorite pickaxe should take no damage";
    private static final String BROKE_CALLBACK = "The break callback should never run for exorite";
    private static final String NOT_BROKEN = "A max-damage exorite piece should read broken";
    private static final String STILL_HARVESTS = "A broken exorite pickaxe should not be the correct tool for stone";
    private static final String STILL_FAST = "A broken exorite pickaxe should mine stone at a bare hand's speed";
    private static final String KEEPS_MODIFIERS = "A broken exorite piece should gather no attribute modifiers: ";
    private static final String NO_BROKEN_LINE = "A broken exorite piece's tooltip should carry the broken line";
    private static final String INTACT_BROKEN_LINE = "An intact exorite piece's tooltip should not carry the broken line";
    private static final String NOT_REPAIRED = "The anvil should repair a broken exorite pickaxe with exorite";
    private static final String REPAIRED_STILL_BROKEN = "A repaired exorite pickaxe should read intact";
    private static final String REPAIRED_CANNOT_HARVEST = "A repaired exorite pickaxe should be the correct tool for stone";

    private ExoriteDurabilityTests() {
    }

    /**
     * A mock player's exorite pickaxe hurt by max damage plus ten through
     * hurtAndBreak stays in the main hand at max damage, and the break
     * callback never runs; a creative player's pickaxe takes no damage.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void survivesZeroDurability(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false; // the mock player is creative, whose items take no damage
        ItemStack pickaxe = new ItemStack(GooItems.EXORITE_PICKAXE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        boolean[] broke = {false};
        pickaxe.hurtAndBreak(pickaxe.getMaxDamage() + OVERKILL_DAMAGE, helper.getLevel(), player,
                item -> broke[0] = true);
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(!held.isEmpty() && held.is(GooItems.EXORITE_PICKAXE.get()), PICKAXE_LOST);
        helper.assertTrue(held.getDamageValue() == held.getMaxDamage(), NOT_AT_FLOOR);
        helper.assertTrue(!broke[0], BROKE_CALLBACK);
        player.getAbilities().instabuild = true;
        ItemStack creativePickaxe = new ItemStack(GooItems.EXORITE_PICKAXE.get());
        creativePickaxe.hurtAndBreak(creativePickaxe.getMaxDamage() + OVERKILL_DAMAGE, helper.getLevel(), player,
                item -> broke[0] = true);
        helper.assertTrue(creativePickaxe.getDamageValue() == 0, CREATIVE_DAMAGED);
        removeMockPlayer(helper, player);
        helper.succeed();
    }

    /**
     * A broken exorite pickaxe mines stone at a bare hand's speed without its
     * drops, and a broken sword and chestplate gather no attribute modifiers.
     *
     * @param helper the gametest helper
     */
    public static void brokenActsAsHand(GameTestHelper helper) {
        ItemStack pickaxe = broken(GooItems.EXORITE_PICKAXE.get());
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(ExoriteGear.isBroken(pickaxe), NOT_BROKEN);
        helper.assertTrue(!pickaxe.isCorrectToolForDrops(stone), STILL_HARVESTS);
        helper.assertTrue(pickaxe.getDestroySpeed(stone) == ExoriteGear.BARE_HAND_DESTROY_SPEED, STILL_FAST);
        for (Item piece : List.of(GooItems.EXORITE_SWORD.get(), GooItems.EXORITE_CHESTPLATE.get())) {
            helper.assertTrue(broken(piece).getAttributeModifiers().modifiers().isEmpty(), KEEPS_MODIFIERS + piece);
        }
        helper.succeed();
    }

    /**
     * A broken exorite pickaxe's tooltip carries the broken line and an
     * intact one's does not.
     *
     * @param helper the gametest helper
     */
    public static void brokenTooltip(GameTestHelper helper) {
        Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());
        List<Component> brokenLines = broken(GooItems.EXORITE_PICKAXE.get())
                .getTooltipLines(context, null, TooltipFlag.NORMAL);
        List<Component> intactLines = new ItemStack(GooItems.EXORITE_PICKAXE.get())
                .getTooltipLines(context, null, TooltipFlag.NORMAL);
        helper.assertTrue(carriesBrokenLine(brokenLines), NO_BROKEN_LINE);
        helper.assertTrue(!carriesBrokenLine(intactLines), INTACT_BROKEN_LINE);
        helper.succeed();
    }

    /**
     * An anvil given a broken exorite pickaxe and one exorite answers a
     * pickaxe below max damage that mines stone for drops again.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void anvilRepair(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AnvilMenu anvil = new AnvilMenu(MENU_ID, player.getInventory());
        anvil.getSlot(ANVIL_LEFT).set(broken(GooItems.EXORITE_PICKAXE.get()));
        anvil.getSlot(ANVIL_RIGHT).set(new ItemStack(GooItems.EXORITE.get()));
        anvil.createResult();
        ItemStack result = anvil.getSlot(ANVIL_RESULT).getItem();
        helper.assertTrue(result.is(GooItems.EXORITE_PICKAXE.get())
                && result.getDamageValue() < result.getMaxDamage(), NOT_REPAIRED);
        helper.assertTrue(!ExoriteGear.isBroken(result), REPAIRED_STILL_BROKEN);
        helper.assertTrue(result.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), REPAIRED_CANNOT_HARVEST);
        removeMockPlayer(helper, player);
        helper.succeed();
    }

    /**
     * Takes a mock player off the server once its test is done. A mock player
     * left in the level tracks later tests' players, and a mod packet sent to
     * those trackers fails on its mock connection, which never negotiated the
     * mod's channels.
     *
     * @param helper the gametest helper
     * @param player the mock player to remove
     */
    private static void removeMockPlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static ItemStack broken(Item piece) {
        ItemStack stack = new ItemStack(piece);
        stack.setDamageValue(stack.getMaxDamage());
        return stack;
    }

    private static boolean carriesBrokenLine(List<Component> lines) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents translatable
                && ExoriteGear.BROKEN_TOOLTIP_KEY.equals(translatable.getKey()));
    }
}

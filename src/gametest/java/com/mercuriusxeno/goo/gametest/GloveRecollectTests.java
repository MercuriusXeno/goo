package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for the glove's shift-click recollect on a ability block, which
 * is a pickup and survives the removal of shift as a targeting mode
 * (decision shift-never-changes-target).
 */
public final class GloveRecollectTests {

    private static final String REMOVAL = "removal";
    private static final BlockPos MARKER_POS = new BlockPos(1, 1, 1);
    private static final Identifier CRYSTAL_CLOUD = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_cloud");
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String NOT_SUCCESS = "Shift-click with the glove on a marker should succeed";
    private static final String GOO_MISSING = "Shift-click recollect should give the marker's stacked goo to the player";
    private static final Identifier METAL_SPIKES = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_spikes");
    private static final int METAL_CHARGES = 4;
    private static final int QUARTER_THROW = 250;
    private static final String NOT_WORTH_LEFT = "Recollect should give goo worth the charges the trap still holds";
    private static final String SPENT_GAVE_GOO = "Recollect gave goo for a trap with no charge left";

    private GloveRecollectTests() {
    }

    /**
     * A sneaking player using the glove on a running marker gets its one
     * goo back and the marker is removed.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void shiftClickRecollectsMarker(GameTestHelper helper) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        AbilityDefinition crystalCloud = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_CLOUD);
        helper.assertTrue(crystalCloud != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.below()), GooTypes.CRYSTAL, Direction.UP,
                crystalCloud);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        ItemStack expected = GooStacks.createForOutput(GooTypes.CRYSTAL, GooStacks.THOUSAND);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, glove);
        player.setShiftKeyDown(true);
        BlockPos pos = helper.absolutePos(MARKER_POS);
        InteractionResult result = glove.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));

        helper.assertTrue(result == InteractionResult.SUCCESS, NOT_SUCCESS);
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.assertTrue(holdsStack(player.getInventory(), expected), GOO_MISSING);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A metal trap with one of its four charges left hands back a quarter of
     * a throw's goo and comes down (decision recollect-returns-charges-left).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void recollectPaysTheChargesLeft(GameTestHelper helper) {
        ServerPlayer player = recollectMetalTrapWith(helper, 1);
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.assertTrue(holdsStack(player.getInventory(), GooStacks.createForOutput(GooTypes.METAL, QUARTER_THROW)),
                NOT_WORTH_LEFT);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A metal trap with no charge left hands back nothing and still comes
     * down (decision recollect-returns-charges-left).
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void recollectOfASpentTrapPaysNothing(GameTestHelper helper) {
        ServerPlayer player = recollectMetalTrapWith(helper, 0);
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.assertTrue(!holdsMetalGoo(player.getInventory()), SPENT_GAVE_GOO);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Lands a metal trap, leaves it the charges given, and shift-clicks it with the glove.
     *
     * @param helper      the gametest helper
     * @param chargesLeft the charges the trap holds when picked up
     * @return the player who picked it up
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer recollectMetalTrapWith(GameTestHelper helper, int chargesLeft) {
        helper.setBlock(MARKER_POS.below(), Blocks.STONE);
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_SPIKES);
        helper.assertTrue(spikes != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.below()), GooTypes.METAL, Direction.UP,
                spikes);
        AbilityBlockEntity be = helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
        be.getFieldEffect().recordCharges(METAL_CHARGES);
        be.getFieldEffect().setChargesSpent(METAL_CHARGES - chargesLeft);
        be.getFieldEffect().recordCharges(chargesLeft);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, glove);
        player.setShiftKeyDown(true);
        BlockPos pos = helper.absolutePos(MARKER_POS);
        InteractionResult result = glove.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(result == InteractionResult.SUCCESS, NOT_SUCCESS);
        return player;
    }

    private static boolean holdsMetalGoo(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (GooTypes.METAL.equals(GooStacks.keyOf(inventory.getItem(slot)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsStack(Inventory inventory, ItemStack expected) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (ItemStack.matches(inventory.getItem(slot), expected)) {
                return true;
            }
        }
        return false;
    }
}

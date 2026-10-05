package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Gametest for the glove's shift-click recollect on a chain marker, which
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
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        AbilityDefinition crystalCloud = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_CLOUD);
        helper.assertTrue(crystalCloud != null, ABILITIES_REQUIRED);
        marker.initChainFromAbility(GooTypes.CRYSTAL, Direction.UP, crystalCloud);
        marker.splat();
        ItemStack expected = GooStacks.createForOutput(GooTypes.CRYSTAL, GooStacks.THOUSAND);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, glove);
        player.setShiftKeyDown(true);
        BlockPos pos = helper.absolutePos(MARKER_POS);
        InteractionResult result = glove.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));

        helper.assertTrue(result == InteractionResult.SUCCESS, NOT_SUCCESS);
        helper.assertBlockNotPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
        helper.assertTrue(holdsStack(player.getInventory(), expected), GOO_MISSING);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
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

package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for an empty-hand right click draining a container into the inventory
 * (decision drained-goo-fills-carried-containers-first).
 */
public final class DrainIntoInventoryTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int CRUCIBLE_ROCK = 5_000;
    private static final int CANISTER_ROOM = 2_000;
    /** A slot off the hotbar's selected slot, so the main hand stays empty. */
    private static final int CANISTER_SLOT = 9;
    private static final String CANISTER_FULL = "The carried rock canister is topped up to its capacity";
    private static final String OMNIBLOB_HOLDS_REST = "One omniblob holds what the canister had no room for";
    private static final String CRUCIBLE_EMPTY = "The crucible gave up all its rock";

    private DrainIntoInventoryTests() {
    }

    /**
     * A crucible holding rock, clicked with an empty hand by a player carrying a rock canister
     * short of full, tops the canister up and puts the rest in one new omniblob.
     *
     * @param helper the gametest helper
     */
    public static void crucibleTopsUpCarriedCanister(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        crucible.insertGoo(ROCK, CRUCIBLE_ROCK);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int capacity = ContainerCapacity.canisterCapacity(0);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, ROCK, capacity - CANISTER_ROOM);
        player.getInventory().setItem(CANISTER_SLOT, canister);

        BlockPos abs = helper.absolutePos(BE_POS);
        helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.assertValueEqual(CanisterItem.getFluidContent(canister).amount(), capacity, CANISTER_FULL);
        helper.assertValueEqual(omniblobVolume(player.getInventory()), CRUCIBLE_ROCK - CANISTER_ROOM,
                OMNIBLOB_HOLDS_REST);
        helper.assertTrue(crucible.getReservoir().isEmpty(), CRUCIBLE_EMPTY);
        helper.succeed();
    }

    /**
     * Sums the rock omniblobs in the inventory, failing on more than one.
     *
     * @param inventory the player inventory
     * @return the one omniblob's volume, or 0
     */
    private static int omniblobVolume(Inventory inventory) {
        int found = 0;
        int volume = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof GooOmniblobItem && BlobStacks.keyOf(stack) == ROCK) {
                found++;
                volume += GooOmniblobItem.getVolume(stack);
            }
        }
        return found == 1 ? volume : -found;
    }
}

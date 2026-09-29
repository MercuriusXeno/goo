package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
    private static final String GOO_HOLDS_REST = "One goo holds what the canister had no room for";
    private static final String CRUCIBLE_EMPTY = "The crucible gave up all its rock";
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int VAT_ROCK = 150_000;
    private static final int VAT_NETHER = 90_000;
    private static final String VAT_ROCK_OUT = "One rock goo holds the vat's rock whole";
    private static final String VAT_NETHER_OUT = "One nether goo holds the vat's nether whole";
    private static final String VAT_EMPTY = "The vat gave up every type in one click";

    private DrainIntoInventoryTests() {
    }

    /**
     * A crucible holding rock, clicked with an empty hand by a player carrying a rock canister
     * short of full, tops the canister up and puts the rest in one new goo.
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
        helper.assertValueEqual(gooVolume(player.getInventory(), ROCK), CRUCIBLE_ROCK - CANISTER_ROOM,
                GOO_HOLDS_REST);
        helper.assertTrue(crucible.getReservoir().isEmpty(), CRUCIBLE_EMPTY);
        helper.succeed();
    }

    /**
     * A vat block holding two types above 64,000 mB each, clicked with an empty hand by a player
     * with an empty inventory, unpacks both whole into one goo per type
     * (decision vat-click-unpacks-into-inventory).
     *
     * @param helper the gametest helper
     */
    public static void vatUnpacksEveryTypeIntoInventory(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(BE_POS, VatBlockEntity.class);
        vat.insertGoo(ROCK, VAT_ROCK);
        vat.insertGoo(NETHER, VAT_NETHER);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        BlockPos abs = helper.absolutePos(BE_POS);
        helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));

        helper.assertValueEqual(gooVolume(player.getInventory(), ROCK), VAT_ROCK, VAT_ROCK_OUT);
        helper.assertValueEqual(gooVolume(player.getInventory(), NETHER), VAT_NETHER, VAT_NETHER_OUT);
        helper.assertTrue(vat.getContents().isEmpty(), VAT_EMPTY);
        helper.succeed();
    }

    /**
     * The volume of the one goo of a type in the inventory.
     *
     * @param inventory the player inventory
     * @param type      the goo type
     * @return the goo's volume, or minus the count when there is not exactly one
     */
    private static int gooVolume(Inventory inventory, ResourceKey<GooTypeDefinition> type) {
        int found = 0;
        int volume = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof GooItem && GooStacks.keyOf(stack) == type) {
                found++;
                volume += GooItem.getVolume(stack);
            }
        }
        return found == 1 ? volume : -found;
    }
}

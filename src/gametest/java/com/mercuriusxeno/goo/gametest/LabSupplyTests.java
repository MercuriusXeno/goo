package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.command.LabCommand;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.lab.LabBox;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabKit;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mercuriusxeno.goo.lab.LabStock;
import com.mercuriusxeno.goo.lab.LabSupply;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gametests for the parts of the Goo Lab read from the registries at build
 * time: the supply row, one station per goo type a datapack included, and
 * the player kit (decision lab-iterates-the-registries).
 */
public final class LabSupplyTests {

    /**
     * The goo type only the seventeenth_goo_type test datapack adds.
     */
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH =
            ResourceKey.create(GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("gootest", "seventeenth"));
    /**
     * Where the supply row's north-west corner lands inside the test instance.
     */
    private static final BlockPos ROW_CORNER = new BlockPos(1, 0, 1);
    private static final String STATION_COUNT = "Supply row should hold one station per registered goo type";
    private static final String NO_STATION = "Supply row should hold a station for ";
    private static final String WRONG_CANISTER = "Station canister should hold goo of its type: ";
    private static final String NO_CHEST = "Station should stand a chest: ";
    private static final String STRAY_ITEM = "Station chest holds an item whose goo value lacks its type: ";
    private static final String EMPTY_CHEST = "The rock station's chest should hold the items that decompose into rock";
    private static final String KIT_MISSING = "Kit should put in the inventory: ";
    private static final String KIT_CANISTERS = "Kit should hold one filled canister per registered goo type";
    private static final String BUILD_NO_KIT = "Build handler should hand the kit to its invoking player";
    private static final String BUILD_FAILED = "Lab build failed: ";
    private static final String SEPARATOR = " ";
    private static final String REMOVAL = "removal";

    private LabSupplyTests() {
    }

    /**
     * Builds the supply row for the registry in force and asserts a station per
     * type, the datapack's seventeenth type included, each canister filled
     * with its type and each chest holding only items that decompose into it.
     *
     * @param helper the gametest helper
     */
    public static void supplyRow(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        LabBox bounds = plan.supply().bounds();
        BlockPos origin = helper.absolutePos(ROW_CORNER).offset(-bounds.min().x(), -bounds.min().y(), -bounds.min().z());
        LabTests.buildRegion(helper, origin, plan, bounds);
        List<String> types = LabStock.gooTypeIds(helper.getLevel());
        helper.assertTrue(plan.supply().stations().size() == types.size(), STATION_COUNT);
        helper.assertTrue(types.contains(SEVENTEENTH.identifier().toString()), NO_STATION + SEVENTEENTH.identifier());
        for (LabSupply.Station station : plan.supply().stations()) {
            assertStation(helper, origin, station);
        }
        LabSupply.Station rock = plan.supply().stations().stream()
                .filter(s -> LabStock.gooType(s).equals(GooTypes.ROCK)).findFirst().orElseThrow();
        ChestBlockEntity rockChest = (ChestBlockEntity) helper.getLevel().getBlockEntity(
                LabBuilder.worldPos(origin, rock.chestOffset()));
        helper.assertFalse(rockChest == null || rockChest.isEmpty(), EMPTY_CHEST);
        helper.succeed();
    }

    /**
     * Runs the kit on a mock player and asserts the glove, each exorite piece
     * and a filled canister per type; then runs the build handler for that
     * player at the test origin and asserts the handler handed the kit again.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void kit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        LabKit.give(player, helper.getLevel());
        assertKit(helper, player.getInventory());
        player.getInventory().clearContent();
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        try {
            LabCommand.buildAt(player.createCommandSourceStack(), LabTests.centredOrigin(helper, plan.bounds()));
        } catch (CommandSyntaxException e) {
            helper.fail(BUILD_FAILED + e.getMessage());
        }
        helper.assertTrue(player.getInventory().contains(new ItemStack(GooItems.GOO_GLOVE.get())), BUILD_NO_KIT);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Asserts a station's canister holds its type and its chest only items holding that type.
     *
     * @param helper  the gametest helper
     * @param origin  the world position of the plan's zero offset
     * @param station the station
     */
    private static void assertStation(GameTestHelper helper, BlockPos origin, LabSupply.Station station) {
        ResourceKey<GooTypeDefinition> type = LabStock.gooType(station);
        CanisterBlockEntity canister = (CanisterBlockEntity) helper.getLevel().getBlockEntity(
                LabBuilder.worldPos(origin, station.canisterOffset()));
        helper.assertTrue(canister != null && type.equals(canister.getSlotGooType(CanisterBlock.CENTER_SLOT)),
                WRONG_CANISTER + station.gooTypeId());
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(
                LabBuilder.worldPos(origin, station.chestOffset()));
        helper.assertTrue(chest != null, NO_CHEST + station.gooTypeId());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            helper.assertTrue(stack.isEmpty() || Goo.GOO_VALUES.lookup(stack).get(type) > 0,
                    STRAY_ITEM + station.gooTypeId() + SEPARATOR + stack);
        }
    }

    /**
     * Asserts an inventory holds every fixed kit item and one filled canister per goo type.
     *
     * @param helper    the gametest helper
     * @param inventory the inventory
     */
    private static void assertKit(GameTestHelper helper, Inventory inventory) {
        List<Item> expected = List.of(GooItems.GOO_GLOVE.get(), GooItems.EXORITE_SWORD.get(),
                GooItems.EXORITE_PICKAXE.get(), GooItems.EXORITE_AXE.get(), GooItems.EXORITE_SHOVEL.get(),
                GooItems.EXORITE_HOE.get(), GooItems.EXORITE_HELMET.get(), GooItems.EXORITE_CHESTPLATE.get(),
                GooItems.EXORITE_LEGGINGS.get(), GooItems.EXORITE_BOOTS.get());
        for (Item item : expected) {
            helper.assertTrue(inventory.contains(new ItemStack(item)), KIT_MISSING + item);
        }
        Set<String> canisterTypes = new HashSet<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof CanisterItem && CanisterItem.getFluidContent(stack).amount() > 0) {
                canisterTypes.add(String.valueOf(CanisterItem.getFluidContent(stack).getGooType().identifier()));
            }
        }
        helper.assertTrue(canisterTypes.equals(Set.copyOf(LabStock.gooTypeIds(helper.getLevel()))), KIT_CANISTERS);
    }
}

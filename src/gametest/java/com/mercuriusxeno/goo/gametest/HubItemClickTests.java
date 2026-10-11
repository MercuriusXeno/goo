package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.HubBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for thousands and gooStacks clicked onto a hub item in inventory
 * (decision hub-item-goo-insert).
 */
public final class HubItemClickTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos HUB_POS = FLOOR_POS.above();
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int GOO_COUNT = 5;
    private static final int GOO_OVERFLOW = 3_000;
    private static final int GOO_FITS = 2_500;
    private static final int PARTIAL_ROOM = 2_000;
    private static final double HALF_BLOCK = 0.5;
    private static final String REGISTERED_OVERRIDE = "Registered hub item carries the override";
    private static final String THOUSANDS_HANDLED = "Goo insert should be handled";
    private static final String OMNITHOUSANDS_HANDLED = "Goo insert should be handled";
    private static final String CURSOR_EMPTIED = "Cursor should be empty once all its goo went in";
    private static final String GOO_REMAINDER = "Goo remainder";
    private static final String BARE_REFUSES = "A hub without canisters refuses";
    private static final String BARE_STAYS_BARE = "Bare hub gains no canisters";
    private static final String CURSOR_COUNT = "Cursor count after refusal";
    private static final String BLOCKED_REFUSES = "Full and mismatched canisters refuse";
    private static final String CONTENTS_UNCHANGED = "Canister contents after refusal";
    private static final String CURSOR_UNTOUCHED = "Cursor never set on refusal";
    private static final String DRAIN_REFUSED = "An empty-cursor secondary click is left to vanilla";
    private static final String CURSOR_STAYS_EMPTY = "Nothing drained onto the cursor";
    private static final String CANISTER_TYPE = "Canister goo type";
    private static final String CANISTER_AMOUNT = "Canister amount";

    private HubItemClickTests() {
    }

    /**
     * A five-goo onto a hub holding an empty canister fills it, empties the cursor,
     * and a hub placed from that stack holds the goo in slot 0.
     *
     * @param helper the gametest helper
     */
    public static void gooInsertFillsCanisterAndPlaces(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hub = hubHolding(new ItemStack(GooItems.CANISTER.get()));
        CursorHolder cursor = new CursorHolder(GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND));

        helper.assertTrue(GooItems.HUB.get() instanceof HubBlockItem, REGISTERED_OVERRIDE);
        helper.assertTrue(primaryClick(hub, cursor, player), THOUSANDS_HANDLED);
        assertCanister(helper, canistersOf(hub).getFirst(), ROCK, GOO_COUNT * GooStacks.THOUSAND);
        helper.assertTrue(cursor.get().isEmpty(), CURSOR_EMPTIED);

        helper.setBlock(FLOOR_POS, Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, hub);
        BlockPos floor = helper.absolutePos(FLOOR_POS);
        helper.useBlock(FLOOR_POS, player,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, HALF_BLOCK, 0), Direction.UP, floor, false));

        HubBlockEntity placed = helper.getBlockEntity(HUB_POS, HubBlockEntity.class);
        assertCanister(helper, placed.getCanister(0), ROCK, GOO_COUNT * GooStacks.THOUSAND);
        helper.succeed();
    }

    /**
     * A goo larger than the free room keeps its remainder; one that fits clears the cursor.
     *
     * @param helper the gametest helper
     */
    public static void gooInsertKeepsRemainder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int capacity = ContainerCapacity.canisterCapacity(0);
        ItemStack hub = hubHolding(canisterWith(ROCK, capacity - PARTIAL_ROOM));
        CursorHolder overflow = new CursorHolder(GooItem.createWithVolume(ROCK, GOO_OVERFLOW));

        helper.assertTrue(primaryClick(hub, overflow, player), OMNITHOUSANDS_HANDLED);
        assertCanister(helper, canistersOf(hub).getFirst(), ROCK, capacity);
        helper.assertValueEqual(GooItem.getVolume(overflow.get()),
                GOO_OVERFLOW - PARTIAL_ROOM, GOO_REMAINDER);

        ItemStack roomyHub = hubHolding(new ItemStack(GooItems.CANISTER.get()));
        CursorHolder fits = new CursorHolder(GooItem.createWithVolume(ROCK, GOO_FITS));
        helper.assertTrue(primaryClick(roomyHub, fits, player), OMNITHOUSANDS_HANDLED);
        assertCanister(helper, canistersOf(roomyHub).getFirst(), ROCK, GOO_FITS);
        helper.assertTrue(fits.get().isEmpty(), CURSOR_EMPTIED);
        helper.succeed();
    }

    /**
     * A hub with no canisters, or only full and mismatched ones, refuses the click and changes nothing.
     *
     * @param helper the gametest helper
     */
    public static void insertRefusedLeavesStacks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bare = new ItemStack(GooItems.HUB.get());
        CursorHolder cursor = new CursorHolder(GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND));

        helper.assertFalse(primaryClick(bare, cursor, player), BARE_REFUSES);
        helper.assertFalse(bare.has(GooDataComponents.HUB_CANISTERS.get()), BARE_STAYS_BARE);
        helper.assertValueEqual(GooStacks.volumeOf(cursor.get()), GOO_COUNT * GooStacks.THOUSAND, CURSOR_COUNT);

        int capacity = ContainerCapacity.canisterCapacity(0);
        ItemStack blocked = hubHolding(canisterWith(ROCK, capacity), canisterWith(NETHER, PARTIAL_ROOM));
        List<CanisterFluidContent> before = contentsOf(blocked);

        helper.assertFalse(primaryClick(blocked, cursor, player), BLOCKED_REFUSES);
        helper.assertValueEqual(contentsOf(blocked), before, CONTENTS_UNCHANGED);
        helper.assertValueEqual(GooStacks.volumeOf(cursor.get()), GOO_COUNT * GooStacks.THOUSAND, CURSOR_COUNT);
        helper.assertFalse(cursor.wasSet(), CURSOR_UNTOUCHED);
        helper.succeed();
    }

    /**
     * An empty-cursor secondary click on a hub holding a filled canister drains nothing
     * and leaves the cursor untouched (decision hub-item-insert-only).
     *
     * @param helper the gametest helper
     */
    public static void secondaryClickDrainsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hub = hubHolding(canisterWith(ROCK, PARTIAL_ROOM));
        List<CanisterFluidContent> before = contentsOf(hub);
        CursorHolder cursor = new CursorHolder(ItemStack.EMPTY);

        boolean handled = hub.getItem().overrideOtherStackedOnMe(hub, cursor.get(),
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.SECONDARY, player, cursor);

        helper.assertFalse(handled, DRAIN_REFUSED);
        helper.assertValueEqual(contentsOf(hub), before, CONTENTS_UNCHANGED);
        helper.assertFalse(cursor.wasSet(), CURSOR_UNTOUCHED);
        helper.assertTrue(cursor.get().isEmpty(), CURSOR_STAYS_EMPTY);
        helper.succeed();
    }

    private static boolean primaryClick(ItemStack hub, CursorHolder cursor, Player player) {
        return hub.getItem().overrideOtherStackedOnMe(hub, cursor.get(),
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.PRIMARY, player, cursor);
    }

    private static ItemStack hubHolding(ItemStack... canisters) {
        ItemStack hub = new ItemStack(GooItems.HUB.get());
        hub.set(GooDataComponents.HUB_CANISTERS.get(), List.of(canisters));
        return hub;
    }

    private static ItemStack canisterWith(ResourceKey<GooTypeDefinition> type, int amount) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, type, amount);
        return canister;
    }

    private static List<ItemStack> canistersOf(ItemStack hub) {
        return hub.getOrDefault(GooDataComponents.HUB_CANISTERS.get(), List.of());
    }

    private static List<CanisterFluidContent> contentsOf(ItemStack hub) {
        return canistersOf(hub).stream().map(CanisterItem::getFluidContent).toList();
    }

    private static void assertCanister(GameTestHelper helper, ItemStack canister,
            ResourceKey<GooTypeDefinition> type, int amount) {
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        helper.assertValueEqual(content.dominantGooType(), type, CANISTER_TYPE);
        helper.assertValueEqual(content.totalVolume(), amount, CANISTER_AMOUNT);
    }
}

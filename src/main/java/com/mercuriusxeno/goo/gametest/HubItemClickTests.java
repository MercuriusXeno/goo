package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.HubBlockItem;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
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
 * Gametests for blobs and omniblobs clicked onto a hub item in inventory
 * (decision hub-item-blob-insert).
 */
public final class HubItemClickTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos HUB_POS = FLOOR_POS.above();
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int BLOB_COUNT = 5;
    private static final int OMNIBLOB_OVERFLOW = 3_000;
    private static final int OMNIBLOB_FITS = 2_500;
    private static final int PARTIAL_ROOM = 2_000;
    private static final double HALF_BLOCK = 0.5;
    private static final String REGISTERED_OVERRIDE = "Registered hub item carries the override";
    private static final String BLOB_HANDLED = "Blob insert should be handled";
    private static final String OMNIBLOB_HANDLED = "Omniblob insert should be handled";
    private static final String CURSOR_EMPTIED = "Cursor should be empty once all its goo went in";
    private static final String OMNIBLOB_REMAINDER = "Omniblob remainder";
    private static final String BARE_REFUSES = "A hub without canisters refuses";
    private static final String BARE_STAYS_BARE = "Bare hub gains no canisters";
    private static final String CURSOR_COUNT = "Cursor count after refusal";
    private static final String BLOCKED_REFUSES = "Full and mismatched canisters refuse";
    private static final String CONTENTS_UNCHANGED = "Canister contents after refusal";
    private static final String CURSOR_UNTOUCHED = "Cursor never set on refusal";
    private static final String CANISTER_TYPE = "Canister goo type";
    private static final String CANISTER_AMOUNT = "Canister amount";

    private HubItemClickTests() {
    }

    /**
     * A blob stack onto a hub holding an empty canister fills it, empties the cursor,
     * and a hub placed from that stack holds the goo in slot 0.
     *
     * @param helper the gametest helper
     */
    public static void blobInsertFillsCanisterAndPlaces(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hub = hubHolding(new ItemStack(GooItems.CANISTER.get()));
        CursorHolder cursor = new CursorHolder(BlobStacks.createBlobStack(ROCK, BLOB_COUNT));

        helper.assertTrue(GooItems.HUB.get() instanceof HubBlockItem, REGISTERED_OVERRIDE);
        helper.assertTrue(primaryClick(hub, cursor, player), BLOB_HANDLED);
        assertCanister(helper, canistersOf(hub).getFirst(), ROCK, BLOB_COUNT * BlobStacks.MB_PER_BLOB);
        helper.assertTrue(cursor.stack.isEmpty(), CURSOR_EMPTIED);

        helper.setBlock(FLOOR_POS, Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, hub);
        BlockPos floor = helper.absolutePos(FLOOR_POS);
        helper.useBlock(FLOOR_POS, player,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, HALF_BLOCK, 0), Direction.UP, floor, false));

        HubBlockEntity placed = helper.getBlockEntity(HUB_POS, HubBlockEntity.class);
        assertCanister(helper, placed.getCanister(0), ROCK, BLOB_COUNT * BlobStacks.MB_PER_BLOB);
        helper.succeed();
    }

    /**
     * An omniblob larger than the free room keeps its remainder; one that fits clears the cursor.
     *
     * @param helper the gametest helper
     */
    public static void omniblobInsertKeepsRemainder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int capacity = ContainerCapacity.canisterCapacity(0);
        ItemStack hub = hubHolding(canisterWith(ROCK, capacity - PARTIAL_ROOM));
        CursorHolder overflow = new CursorHolder(GooOmniblobItem.createWithVolume(ROCK, OMNIBLOB_OVERFLOW));

        helper.assertTrue(primaryClick(hub, overflow, player), OMNIBLOB_HANDLED);
        assertCanister(helper, canistersOf(hub).getFirst(), ROCK, capacity);
        helper.assertValueEqual(GooOmniblobItem.getVolume(overflow.stack),
                OMNIBLOB_OVERFLOW - PARTIAL_ROOM, OMNIBLOB_REMAINDER);

        ItemStack roomyHub = hubHolding(new ItemStack(GooItems.CANISTER.get()));
        CursorHolder fits = new CursorHolder(GooOmniblobItem.createWithVolume(ROCK, OMNIBLOB_FITS));
        helper.assertTrue(primaryClick(roomyHub, fits, player), OMNIBLOB_HANDLED);
        assertCanister(helper, canistersOf(roomyHub).getFirst(), ROCK, OMNIBLOB_FITS);
        helper.assertTrue(fits.stack.isEmpty(), CURSOR_EMPTIED);
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
        CursorHolder cursor = new CursorHolder(BlobStacks.createBlobStack(ROCK, BLOB_COUNT));

        helper.assertFalse(primaryClick(bare, cursor, player), BARE_REFUSES);
        helper.assertFalse(bare.has(GooDataComponents.HUB_CANISTERS.get()), BARE_STAYS_BARE);
        helper.assertValueEqual(cursor.stack.getCount(), BLOB_COUNT, CURSOR_COUNT);

        int capacity = ContainerCapacity.canisterCapacity(0);
        ItemStack blocked = hubHolding(canisterWith(ROCK, capacity), canisterWith(NETHER, PARTIAL_ROOM));
        List<CanisterFluidContent> before = contentsOf(blocked);

        helper.assertFalse(primaryClick(blocked, cursor, player), BLOCKED_REFUSES);
        helper.assertValueEqual(contentsOf(blocked), before, CONTENTS_UNCHANGED);
        helper.assertValueEqual(cursor.stack.getCount(), BLOB_COUNT, CURSOR_COUNT);
        helper.assertFalse(cursor.setCalled, CURSOR_UNTOUCHED);
        helper.succeed();
    }

    private static boolean primaryClick(ItemStack hub, CursorHolder cursor, Player player) {
        return hub.getItem().overrideOtherStackedOnMe(hub, cursor.stack,
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
        helper.assertValueEqual(content.getGooType(), type, CANISTER_TYPE);
        helper.assertValueEqual(content.amount(), amount, CANISTER_AMOUNT);
    }

    /**
     * The cursor as a SlotAccess that records whether it was ever set.
     */
    private static final class CursorHolder implements SlotAccess {
        private ItemStack stack;
        private boolean setCalled;

        CursorHolder(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack get() {
            return stack;
        }

        @Override
        public boolean set(ItemStack replacement) {
            stack = replacement;
            setCalled = true;
            return true;
        }
    }
}

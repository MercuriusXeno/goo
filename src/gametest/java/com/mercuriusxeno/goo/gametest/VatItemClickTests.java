package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Gametests for blobs and omniblobs clicked onto a vat item in inventory
 * (decisions vat-item-insert-shared and vat-item-drain-shared).
 */
public final class VatItemClickTests {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int BLOB_COUNT = 5;
    private static final int OMNIBLOB_OVERFLOW = 3_000;
    private static final int PARTIAL_ROOM = 2_000;
    private static final int ROCK_PAST_CAP = 5_000;
    private static final int NETHER_HELD = 3_000;
    private static final String REGISTERED_OVERRIDE = "Registered vat item carries the override";
    private static final String BLOB_HANDLED = "Blob insert should be handled";
    private static final String OMNIBLOB_HANDLED = "Omniblob insert should be handled";
    private static final String CURSOR_EMPTIED = "Cursor should be empty once all its goo went in";
    private static final String OMNIBLOB_REMAINDER = "Omniblob remainder";
    private static final String FULL_REFUSES = "A full vat item refuses";
    private static final String CONTENTS_UNCHANGED = "Vat contents after refusal";
    private static final String CURSOR_COUNT = "Cursor count after refusal";
    private static final String CURSOR_UNTOUCHED = "Cursor never set on refusal";
    private static final String VAT_VOLUME = "Vat volume of the inserted type";
    private static final String DRAIN_HANDLED = "Drain from a filled vat item should be handled";
    private static final String DRAINED_TYPE = "Cursor holds the larger type";
    private static final String DRAINED_VOLUME = "Cursor holds one blob cap";
    private static final String LARGER_LEFT = "Larger type left in the vat";
    private static final String SMALLER_LEFT = "Smaller type stays in the vat";
    private static final String EMPTY_REFUSES = "Drain from an empty vat item answers false";
    private static final String CURSOR_STAYS_EMPTY = "Nothing drained onto the cursor";

    private VatItemClickTests() {
    }

    /**
     * A blob stack onto an empty vat item fills it and empties the cursor;
     * the same stack onto a full vat item is refused and neither stack changes.
     *
     * @param helper the gametest helper
     */
    public static void blobInsertFillsVatAndFullRefuses(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack vat = new ItemStack(GooItems.VAT.get());
        CursorHolder cursor = new CursorHolder(BlobStacks.createBlobStack(ROCK, BLOB_COUNT));

        helper.assertTrue(GooItems.VAT.get() instanceof VatBlockItem, REGISTERED_OVERRIDE);
        helper.assertTrue(primaryClick(vat, cursor, player), BLOB_HANDLED);
        helper.assertValueEqual(VatBlockItem.getGooContents(vat).getVolume(ROCK),
                BLOB_COUNT * BlobStacks.MB_PER_BLOB, VAT_VOLUME);
        helper.assertTrue(cursor.get().isEmpty(), CURSOR_EMPTIED);

        ItemStack full = vatWith(ROCK, ContainerCapacity.vatCapacity(0));
        GooContents before = VatBlockItem.getGooContents(full);
        CursorHolder refused = new CursorHolder(BlobStacks.createBlobStack(ROCK, BLOB_COUNT));

        helper.assertFalse(primaryClick(full, refused, player), FULL_REFUSES);
        helper.assertValueEqual(VatBlockItem.getGooContents(full), before, CONTENTS_UNCHANGED);
        helper.assertValueEqual(refused.get().getCount(), BLOB_COUNT, CURSOR_COUNT);
        helper.assertFalse(refused.wasSet(), CURSOR_UNTOUCHED);
        helper.succeed();
    }

    /**
     * An omniblob larger than the vat item's free room fills it and keeps its remainder.
     *
     * @param helper the gametest helper
     */
    public static void omniblobInsertKeepsRemainder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int capacity = ContainerCapacity.vatCapacity(0);
        ItemStack vat = vatWith(ROCK, capacity - PARTIAL_ROOM);
        CursorHolder cursor = new CursorHolder(GooOmniblobItem.createWithVolume(ROCK, OMNIBLOB_OVERFLOW));

        helper.assertTrue(primaryClick(vat, cursor, player), OMNIBLOB_HANDLED);
        helper.assertValueEqual(VatBlockItem.getGooContents(vat).getVolume(ROCK), capacity, VAT_VOLUME);
        helper.assertValueEqual(GooOmniblobItem.getVolume(cursor.get()),
                OMNIBLOB_OVERFLOW - PARTIAL_ROOM, OMNIBLOB_REMAINDER);
        helper.succeed();
    }

    /**
     * An empty-cursor secondary click on a vat item holding two types drains one blob cap
     * of the larger type onto the cursor; on an empty vat item it answers false.
     *
     * @param helper the gametest helper
     */
    public static void secondaryClickDrainsLargerType(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack vat = vatWith(ROCK, ContainerCapacity.BLOB_CAP + ROCK_PAST_CAP);
        VatBlockItem.addGoo(vat, NETHER, NETHER_HELD);
        CursorHolder cursor = new CursorHolder(ItemStack.EMPTY);

        helper.assertTrue(secondaryClick(vat, cursor, player), DRAIN_HANDLED);
        helper.assertValueEqual(BlobStacks.keyOf(cursor.get()), ROCK, DRAINED_TYPE);
        helper.assertValueEqual(BlobStacks.volumeOf(cursor.get()), ContainerCapacity.BLOB_CAP, DRAINED_VOLUME);
        GooContents left = VatBlockItem.getGooContents(vat);
        helper.assertValueEqual(left.getVolume(ROCK), ROCK_PAST_CAP, LARGER_LEFT);
        helper.assertValueEqual(left.getVolume(NETHER), NETHER_HELD, SMALLER_LEFT);

        ItemStack empty = new ItemStack(GooItems.VAT.get());
        CursorHolder untouched = new CursorHolder(ItemStack.EMPTY);
        helper.assertFalse(secondaryClick(empty, untouched, player), EMPTY_REFUSES);
        helper.assertFalse(untouched.wasSet(), CURSOR_UNTOUCHED);
        helper.assertTrue(untouched.get().isEmpty(), CURSOR_STAYS_EMPTY);
        helper.succeed();
    }

    private static boolean secondaryClick(ItemStack vat, CursorHolder cursor, Player player) {
        return vat.getItem().overrideOtherStackedOnMe(vat, cursor.get(),
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.SECONDARY, player, cursor);
    }

    private static boolean primaryClick(ItemStack vat, CursorHolder cursor, Player player) {
        return vat.getItem().overrideOtherStackedOnMe(vat, cursor.get(),
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.PRIMARY, player, cursor);
    }

    private static ItemStack vatWith(ResourceKey<GooTypeDefinition> type, int amount) {
        ItemStack vat = new ItemStack(GooItems.VAT.get());
        VatBlockItem.addGoo(vat, type, amount);
        return vat;
    }
}

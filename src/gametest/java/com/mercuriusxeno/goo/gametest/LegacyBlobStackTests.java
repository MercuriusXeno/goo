package com.mercuriusxeno.goo.gametest;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.JsonOps;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Gametests for a goo:goo_blob stack saved by an earlier build, which the registry
 * alias loads as an omniblob of count n with no BLOB_VOLUME (decision blobs-become-omniblobs):
 * it behaves as one omniblob of n x 1,000 mB wherever it sits, never inventory-ticked.
 */
public final class LegacyBlobStackTests {

    private static final int LEGACY_COUNT = 3;
    private static final int LEGACY_VOLUME = LEGACY_COUNT * BlobStacks.MB_PER_BLOB;
    private static final String LEGACY_ID = "goo:goo_blob";
    private static final String GOO_TYPE_COMPONENT = "goo:goo_type";
    private static final String LOADS_AS_OMNIBLOB = "A saved goo:goo_blob stack should load as an omniblob";
    private static final String LOADED_COUNT = "Count of the loaded legacy stack";
    private static final String LOADED_VOLUME = "Volume of the loaded legacy stack";
    private static final String LOADED_TYPE = "A loaded legacy stack should keep its goo type";
    private static final String HANDLED = "The right-click on the legacy stack should be handled";
    private static final String SLOT_COUNT = "Slot count after halving the legacy stack";
    private static final String SLOT_VOLUME = "Slot volume after halving the legacy stack";
    private static final String CURSOR_VOLUME = "Cursor volume after halving the legacy stack";
    private static final String DEPLETED = "A legacy stack depleted by its whole volume should be empty";

    private LegacyBlobStackTests() {
    }

    /**
     * A saved 3-blob goo:goo_blob stack loads as one omniblob of 3,000 mB of rock;
     * placed in a chest's list the way a load places it, an empty-cursor right-click
     * leaves one omniblob of 1,500 mB in the slot and 1,500 mB on the cursor, and
     * depleting a loaded legacy stack by its whole volume leaves nothing.
     *
     * @param helper the gametest helper
     */
    public static void legacyStackHalvesAndDepletesWithoutDuplication(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = new SimpleContainer(1);
        ItemStack legacy = legacyStack(helper);
        // A chest's load sets the saved stack straight into its list, unclamped (ContainerHelper.loadAllItems).
        chest.getItems().set(0, legacy);
        helper.assertTrue(legacy.is(GooItems.GOO_OMNIBLOB.get()), LOADS_AS_OMNIBLOB);
        helper.assertValueEqual(legacy.getCount(), 1, LOADED_COUNT);
        helper.assertValueEqual(BlobStacks.volumeOf(legacy), LEGACY_VOLUME, LOADED_VOLUME);
        helper.assertTrue(BlobStacks.keyOf(legacy) == GooTypes.ROCK, LOADED_TYPE);
        Slot slot = new Slot(chest, 0, 0, 0);
        CursorHolder cursor = new CursorHolder(ItemStack.EMPTY);

        helper.assertTrue(legacy.getItem().overrideOtherStackedOnMe(legacy, cursor.get(), slot,
                ClickAction.SECONDARY, player, cursor), HANDLED);
        helper.assertValueEqual(slot.getItem().getCount(), 1, SLOT_COUNT);
        helper.assertValueEqual(BlobStacks.volumeOf(slot.getItem()), LEGACY_VOLUME / 2, SLOT_VOLUME);
        helper.assertValueEqual(BlobStacks.volumeOf(cursor.get()), LEGACY_VOLUME / 2, CURSOR_VOLUME);

        ItemStack depleted = legacyStack(helper);
        BlobStacks.deplete(depleted, LEGACY_VOLUME, player);
        helper.assertTrue(depleted.isEmpty(), DEPLETED);
        helper.succeed();
    }

    /**
     * Loads a 3-blob goo:goo_blob stack the way a saved world does, through ItemStack.CODEC.
     *
     * @param helper the gametest helper
     * @return the stack the load answers
     */
    private static ItemStack legacyStack(GameTestHelper helper) {
        JsonObject components = new JsonObject();
        components.addProperty(GOO_TYPE_COMPONENT, GooTypes.ROCK.identifier().toString());
        JsonObject saved = new JsonObject();
        saved.addProperty("id", LEGACY_ID);
        saved.addProperty("count", LEGACY_COUNT);
        saved.add("components", components);
        return ItemStack.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                saved).getOrThrow();
    }
}

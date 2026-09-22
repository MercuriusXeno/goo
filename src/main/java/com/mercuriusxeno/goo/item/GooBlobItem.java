package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypeNames;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * The one stackable goo blob item, carrying its type in the GOO_TYPE data
 * component (decision generic-goo-items). Each blob represents exactly
 * 1,000 mB of goo. Stacks to 64 (vanilla default); two types never stack,
 * since their components differ. For volumes that don't fit in a blob
 * stack, see {@link GooOmniblobItem}.
 *
 * <p>Migration: old volumetric blobs with a BLOB_VOLUME component are converted
 * on inventory tick to the new stackable format.</p>
 */
public class GooBlobItem extends Item implements IGooItemInteraction {

    /**
     * Volume of one blob in microblobs.
     */
    public static final int VOLUME_PER_BLOB = BlobStacks.MB_PER_BLOB;

    /**
     * Creates the blob item.
     *
     * @param properties item properties (default stack size 64)
     */
    public GooBlobItem(Properties properties) {
        super(properties);
    }

    /**
     * The type key a blob or omniblob stack carries.
     *
     * @param stack an item stack
     * @return the key in its GOO_TYPE component, or null for a stack carrying none
     */
    public static @Nullable ResourceKey<GooTypeDefinition> keyOf(ItemStack stack) {
        return stack.get(GooDataComponents.GOO_TYPE.get());
    }

    /**
     * Whether two stacks carry the same goo type.
     *
     * @param a one stack
     * @param b another stack
     * @return true when both carry one type
     */
    public static boolean sameType(ItemStack a, ItemStack b) {
        ResourceKey<GooTypeDefinition> key = keyOf(a);
        return key != null && Objects.equals(key, keyOf(b));
    }

    /**
     * Returns the display name as "[Type] Blob".
     *
     * @param stack the item stack
     * @return the display name component
     */
    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        return GooTypeNames.blobName(keyOf(stack));
    }

    /**
     * Migration: converts old volumetric blobs (with BLOB_VOLUME component) to
     * the new stackable format. Creates an omniblob for any remainder.
     *
     * @param stack  the item stack
     * @param level  the server level
     * @param entity the entity holding this item
     * @param slot   the equipment slot
     */
    @Override
    public void inventoryTick(@NonNull ItemStack stack, @NonNull ServerLevel level, @NonNull Entity entity,
                              EquipmentSlot slot) {
        if (!(entity instanceof Player player)) {
            return;
        }
        Integer oldVolume = stack.get(GooDataComponents.BLOB_VOLUME.get());
        if (oldVolume == null) {
            return;
        }

        migrateOldBlob(stack, oldVolume, player);
    }

    /**
     * Converts a legacy volumetric blob to stackable format plus omniblob remainder.
     *
     * @param stack     the item stack to migrate
     * @param oldVolume the legacy volume in microblobs
     * @param player    the player holding the stack
     */
    private static void migrateOldBlob(ItemStack stack, int oldVolume, Player player) {
        stack.remove(GooDataComponents.BLOB_VOLUME.get());

        int wholeBlobs = BlobStacks.wholeBlobs(oldVolume);
        int remainder = BlobStacks.remainder(oldVolume);

        int newCount = Math.min(wholeBlobs, BlobStacks.MAX_STACK);
        stack.setCount(newCount);

        int overflowVolume = computeOverflow(wholeBlobs, remainder);
        distributeOverflow(player, overflowVolume, stack, newCount);
    }

    /**
     * Computes the leftover volume that exceeds the max blob stack size.
     *
     * @param wholeBlobs the total number of whole blobs from the legacy volume
     * @param remainder  the sub-blob leftover in microblobs
     * @return the overflow volume in microblobs (excess blobs beyond 64 plus remainder)
     */
    private static int computeOverflow(int wholeBlobs, int remainder) {
        return (wholeBlobs > BlobStacks.MAX_STACK)
                ? (wholeBlobs - BlobStacks.MAX_STACK) * BlobStacks.MB_PER_BLOB + remainder
                : remainder;
    }

    /**
     * Creates an omniblob for overflow volume, or clears the stack if nothing remains.
     *
     * @param player         the player to receive the overflow omniblob
     * @param overflowVolume the excess volume in microblobs to distribute
     * @param stack          the original blob stack being migrated
     * @param newCount       the stack count after capping at max blob stack size
     */
    private static void distributeOverflow(Player player, int overflowVolume, ItemStack stack, int newCount) {
        if (overflowVolume > 0) {
            ItemStack omniblob = GooOmniblobItem.createWithVolume(keyOf(stack), overflowVolume);
            PlayerUtils.addOrDrop(player, omniblob);
        }
        if (newCount <= 0 && overflowVolume <= 0) {
            stack.setCount(0);
        }
    }

    /**
     * When a blob stack of the same type is clicked onto a full stack of 64,
     * or when combined count exceeds 64: create an omniblob with total volume.
     *
     * @param thisStack    the blob stack in the slot
     * @param cursor       the item stack on the cursor
     * @param slot         the inventory slot
     * @param action       the click action
     * @param player       the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack thisStack, @NonNull ItemStack cursor,
                                            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
                                            @NonNull SlotAccess cursorAccess) {
        if (!(cursor.getItem() instanceof GooBlobItem) || !sameType(thisStack, cursor)) {
            return false;
        }
        if (action != ClickAction.PRIMARY) {
            return false;
        }

        int totalCount = thisStack.getCount() + cursor.getCount();
        if (totalCount <= thisStack.getMaxStackSize()) {
            return false;
        }

        int totalVolume = totalCount * BlobStacks.MB_PER_BLOB;
        ItemStack omniblob = GooOmniblobItem.createWithVolume(keyOf(thisStack), totalVolume);
        thisStack.setCount(0);
        slot.set(omniblob);
        cursorAccess.set(ItemStack.EMPTY);
        return true;
    }

    /**
     * Returns BLOB_INSERT so canister blocks route to blob pour logic.
     *
     * @return the blob insert interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.BLOB_INSERT;
    }
}

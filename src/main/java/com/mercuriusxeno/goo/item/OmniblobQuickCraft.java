package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;

/**
 * Pure utility for omniblob drag-to-distribute (quickcraft) math.
 * Separated from mixin code for testability.
 */
public final class OmniblobQuickCraft {

    private static final int ONE_MICROBLOB = 1;

    private OmniblobQuickCraft() {}

    /**
     * Returns true if the stack is an omniblob with enough volume to distribute.
     *
     * @param stack the carried item stack
     * @return true if this is an omniblob with throwable volume
     */
    public static boolean isOmniblobQuickCraft(ItemStack stack) {
        return stack.getItem() instanceof GooOmniblobItem
            && GooOmniblobItem.getVolume(stack) > 0;
    }

    /**
     * Computes per-slot volume for left-click (charitable) drag distribution.
     * Integer division: volume / slotCount, floored.
     *
     * @param totalVolume total volume in microblobs
     * @param slotCount   number of slots being distributed to
     * @return volume per slot in microblobs
     */
    public static int charitablePerSlot(int totalVolume, int slotCount) {
        if (slotCount <= 0) { return 0; }
        return totalVolume / slotCount;
    }

    /**
     * The unit one right-drag slot or one cursor right-click on an empty slot
     * places, read from the carried volume: one blob (1,000 mB) while the
     * cursor holds more than one blob (decision right-drag-over-one-blob-places-blobs),
     * and one microblob (1 mB) at one blob or less
     * (decision right-drag-one-blob-places-microblobs).
     *
     * @param carriedVolume the volume on the cursor, in microblobs
     * @return the volume one slot receives, in microblobs
     */
    public static int greedyPerSlot(int carriedVolume) {
        return carriedVolume > BlobStacks.MB_PER_BLOB ? BlobStacks.MB_PER_BLOB : ONE_MICROBLOB;
    }
}

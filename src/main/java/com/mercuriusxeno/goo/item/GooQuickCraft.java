package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;

/**
 * Pure utility for goo drag-to-distribute (quickcraft) math.
 * Separated from mixin code for testability.
 */
public final class GooQuickCraft {

    private static final int ONE_UNIT = 1;

    private GooQuickCraft() {}

    /**
     * Returns true if the stack is a goo with enough volume to distribute.
     *
     * @param stack the carried item stack
     * @return true if this is a goo with throwable volume
     */
    public static boolean isGooQuickCraft(ItemStack stack) {
        return stack.getItem() instanceof GooItem
            && GooItem.getVolume(stack) > 0;
    }

    /**
     * Computes per-slot volume for left-click (charitable) drag distribution.
     * Integer division: volume / slotCount, floored.
     *
     * @param totalVolume total volume
     * @param slotCount   number of slots being distributed to
     * @return volume per slot
     */
    public static int charitablePerSlot(int totalVolume, int slotCount) {
        if (slotCount <= 0) { return 0; }
        return totalVolume / slotCount;
    }

    /**
     * The unit one right-drag slot or one cursor right-click on an empty slot
     * places, read from the carried volume: one goo (1,000 mB) while the
     * cursor holds more than one goo (decision right-drag-over-a-thousand-places-thousands),
     * and one unit (1 mB) at one goo or less
     * (decision right-drag-under-a-thousand-places-ones).
     *
     * @param carriedVolume the volume on the cursor
     * @return the volume one slot receives
     */
    public static int greedyPerSlot(int carriedVolume) {
        return carriedVolume > GooStacks.THOUSAND ? GooStacks.THOUSAND : ONE_UNIT;
    }
}

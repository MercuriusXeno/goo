package com.mercuriusxeno.goo.gametest;

import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.ItemStack;

/**
 * The cursor as a SlotAccess that records whether it was ever set.
 */
final class CursorHolder implements SlotAccess {
    private ItemStack stack;
    private boolean setCalled;

    /**
     * Creates a cursor holding the given stack.
     *
     * @param stack the stack on the cursor
     */
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

    /**
     * Answers whether anything set the cursor.
     *
     * @return true once set was called
     */
    boolean wasSet() {
        return setCalled;
    }
}

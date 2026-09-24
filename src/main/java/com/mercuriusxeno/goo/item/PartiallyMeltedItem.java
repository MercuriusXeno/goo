package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Represents an item mid-extraction in the crucible. Holds goo values
 * via GooContents (multi-type goo storage).
 * As the crucible operates, goo is drained from this item into the reservoir.
 * If the crucible breaks, the partially melted item drops with remaining goo.
 */
public class PartiallyMeltedItem extends Item {

    /**
     * Creates a partially melted item. Unstackable.
     *
     * @param properties the item properties
     */
    public PartiallyMeltedItem(Properties properties) {
        super(properties);
    }

    /**
     * Returns the goo contents from the stack, or EMPTY if absent.
     *
     * @param stack the item stack
     * @return the goo contents, never null
     */
    public static GooContents getContents(ItemStack stack) {
        GooContents contents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return contents != null ? contents : GooContents.EMPTY;
    }

    /**
     * Sets the goo contents on the stack.
     *
     * @param stack    the item stack
     * @param contents the goo contents to set
     */
    public static void setContents(ItemStack stack, GooContents contents) {
        stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
    }

    /**
     * Creates a partially melted ItemStack pre-loaded with the given goo contents.
     * Used when the crucible begins melting an item.
     *
     * @param contents the goo contents to embed
     * @return a new partially melted item stack
     */
    public static ItemStack createWith(GooContents contents) {
        ItemStack stack = new ItemStack(
            com.mercuriusxeno.goo.registry.GooItems.PARTIALLY_MELTED_ITEM.get());
        setContents(stack, contents);
        return stack;
    }

    /**
     * Returns true if all goo has been fully drained from this item.
     *
     * @param stack the item stack
     * @return true if no goo remains
     */
    public static boolean isFullyMelted(ItemStack stack) {
        return getContents(stack).isEmpty();
    }
}

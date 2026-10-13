package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * Shared add/remove logic for any item stack that carries a
 * {@link GooDataComponents#GOO_CONTENTS} component: the cap-and-accept pattern
 * {@link VatBlockItem} wraps, over a base capacity the held void grows.
 *
 * decision each-void-mb-adds-n-plus-one-capacity
 */
public final class GooContentsOps {

    private GooContentsOps() {}

    /**
     * Adds goo to a stack's {@code GOO_CONTENTS}, capped at the room the type
     * has. Writes the component back only if any amount was accepted.
     *
     * @param stack  the item stack holding the goo contents
     * @param type   the goo type to add
     * @param amount desired volume
     * @param base   the stack's capacity with no void (mB)
     * @return the amount actually accepted (0 if full or non-positive input)
     */
    public static int addGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount, int base) {
        if (amount <= 0) { return 0; }
        GooContents contents = getContents(stack);
        int accepted = Math.min(amount, contents.roomFor(type, base));
        if (accepted <= 0) { return 0; }
        setContents(stack, contents.withAdded(type, accepted));
        return accepted;
    }

    /**
     * Removes goo of the given type from a stack's {@code GOO_CONTENTS},
     * capped at what may leave.
     *
     * @param stack  the item stack holding the goo contents
     * @param type   the goo type to remove
     * @param amount desired volume to remove
     * @param base   the stack's capacity with no void (mB)
     * @return the amount actually removed
     */
    public static int removeGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount, int base) {
        GooContents contents = getContents(stack);
        int toRemove = Math.min(amount, contents.releasableVolumeOf(type, base));
        if (toRemove <= 0) { return 0; }
        setContents(stack, contents.withRemoved(type, toRemove));
        return toRemove;
    }

    /**
     * Reads the goo contents from a stack, returning {@link GooContents#EMPTY}
     * if the component is absent.
     *
     * @param stack the item stack to read
     * @return the goo contents, never null
     */
    private static GooContents getContents(ItemStack stack) {
        GooContents c = stack.get(GooDataComponents.GOO_CONTENTS.get());
        return c != null ? c : GooContents.EMPTY;
    }

    /**
     * Writes goo contents to a stack, removing the component outright when empty
     * so empty stacks do not carry dead data.
     *
     * @param stack    the item stack to write
     * @param contents the goo contents to set
     */
    private static void setContents(ItemStack stack, GooContents contents) {
        if (contents.isEmpty()) {
            stack.remove(GooDataComponents.GOO_CONTENTS.get());
        } else {
            stack.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }
}

package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/**
 * An item whose stacks carry goo the player can throw: an omniblob, a canister,
 * a hub carrying canisters or a vat. The source scanner reads every
 * carrier through this interface, so a new goo-carrying item joins the scan by
 * implementing it (decision hosts-answer-bounds-through-interfaces).
 */
public interface GooCarrierItem {

    /**
     * The passes a deplete runs, in declaration order: the carrier drawn first leads.
     */
    enum DepletionPass {
        /** Omniblob stacks. */
        OMNIBLOB,
        /** Canisters, a hub's carried canisters included. */
        CANISTER,
        /** Vat items. */
        VAT
    }

    /**
     * @return the pass whose deplete draws from this carrier
     */
    DepletionPass depletionPass();

    /**
     * @param stack a stack of this item
     * @return every goo type the stack carries, with its volume in mB
     */
    Map<ResourceKey<GooTypeDefinition>, Integer> gooContents(ItemStack stack);

    /**
     * @param stack a stack of this item
     * @param type  the goo type
     * @return the mB of that type the stack carries
     */
    default int gooVolume(ItemStack stack, ResourceKey<GooTypeDefinition> type) {
        return gooContents(stack).getOrDefault(type, 0);
    }

    /**
     * Removes up to the amount of a type from the stack.
     *
     * @param stack  a stack of this item
     * @param type   the goo type
     * @param amount the mB wanted
     * @return the mB removed
     */
    int drawGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount);
}

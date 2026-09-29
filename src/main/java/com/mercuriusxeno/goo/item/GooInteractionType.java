package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import org.jspecify.annotations.Nullable;

/**
 * Classifies how a held item interacts with a goo machine block: one row per click the
 * machines answer, so each machine's dispatcher is a switch over these rows
 * (decision every-machine-clicks-through-the-dispatcher).
 */
public enum GooInteractionType {

    /** Tuner: pass through to let the tuner's own use logic handle it. */
    TUNER_PASS,

    /** Gasket: pass through to let the gasket's own use logic install it. */
    GASKET_INSTALL,

    /** Canister item on an empty or missed slot: insert it. */
    CANISTER_INSERT,

    /** Canister item on a slot holding a canister: pick the held-in-block canister up. */
    CANISTER_PICKUP,

    /** Blob or omniblob: pour goo volume into a matching slot. */
    BLOB_INSERT,

    /** A bucket or any other item carrying a fluid handler: fill from or drain into the slot. */
    FLUID_CONTAINER,

    /** Flint and steel: spark a machine that lights. */
    SPARK;

    /**
     * Returns true if this interaction type should be subject to the interaction cooldown:
     * a canister moved in or out leaves the hand holding a canister, so a held click would
     * swap it straight back. Blobs are self-limiting (consumed on use).
     *
     * @return true if cooldown applies
     */
    public boolean requiresCooldown() {
        return this == CANISTER_INSERT || this == CANISTER_PICKUP;
    }

    /**
     * Reports whether the machine passes this click on so the held item's own use runs.
     *
     * @return true for the tuner and the gasket
     */
    public boolean passesToItem() {
        return this == TUNER_PASS || this == GASKET_INSTALL;
    }

    /**
     * Resolves the interaction type for the given item stack aimed at no canister slot.
     *
     * @param stack the held item stack
     * @return the interaction type, or null if the item has no goo interaction
     */
    public static @Nullable GooInteractionType classify(ItemStack stack) {
        return classify(stack, false);
    }

    /**
     * Resolves the interaction type for the given item stack and the slot it aims at.
     * Goo items self-classify via {@link IGooItemInteraction}, flint and steel sparks, and
     * any other item with a fluid handler is a fluid container.
     *
     * @param stack            the held item stack
     * @param targetSlotFilled whether the aimed canister slot holds a canister
     * @return the interaction type, or null if the item has no goo interaction
     */
    public static @Nullable GooInteractionType classify(ItemStack stack, boolean targetSlotFilled) {
        GooInteractionType selfClassified = itemKind(stack);
        boolean fluidContainer = selfClassified == null && !stack.isEmpty()
                && stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack)) != null;
        return resolve(selfClassified, fluidContainer, targetSlotFilled);
    }

    /**
     * The type an item names by its kind alone: what a goo item says of itself, or a spark for
     * flint and steel.
     *
     * @param stack the held item stack
     * @return the type, or null for any other item
     */
    private static @Nullable GooInteractionType itemKind(ItemStack stack) {
        if (stack.getItem() instanceof IGooItemInteraction gooItem) {
            return gooItem.canisterInteraction();
        }
        return stack.is(Items.FLINT_AND_STEEL) ? SPARK : null;
    }

    /**
     * The classification rule over what the item says of itself and the slot it aims at.
     *
     * @param selfClassified   the type a goo item names for itself, or null
     * @param fluidContainer   whether the item carries a fluid handler
     * @param targetSlotFilled whether the aimed canister slot holds a canister
     * @return the interaction type, or null if the item has no goo interaction
     */
    static @Nullable GooInteractionType resolve(
            @Nullable GooInteractionType selfClassified, boolean fluidContainer, boolean targetSlotFilled) {
        if (selfClassified == CANISTER_INSERT && targetSlotFilled) {
            return CANISTER_PICKUP;
        }
        if (selfClassified != null) {
            return selfClassified;
        }
        return fluidContainer ? FLUID_CONTAINER : null;
    }
}

package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * The sprayer: a gold caulk gun carrying one canister, which inventory clicks put in, take out
 * and swap the way a bundle takes items.
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 * decision sprayer-takes-gives-and-swaps-its-canister-by-clicks
 */
public class SprayerItem extends Item {

    /**
     * Creates the sprayer item.
     *
     * @param properties the item properties
     */
    public SprayerItem(Properties properties) {
        super(properties);
    }

    /**
     * The canister the sprayer carries, or the empty stack.
     *
     * @param sprayer the sprayer stack
     * @return a copy of the fitted canister, or ItemStack.EMPTY
     */
    public static ItemStack fittedCanister(ItemStack sprayer) {
        ItemStack fitted = sprayer.get(GooDataComponents.SPRAYER_CANISTER.get());
        return fitted == null ? ItemStack.EMPTY : fitted.copy();
    }

    /**
     * Fits a canister into the sprayer, or empties it for the empty stack.
     *
     * @param sprayer  the sprayer stack
     * @param canister the canister to fit, or ItemStack.EMPTY
     */
    public static void fitCanister(ItemStack sprayer, ItemStack canister) {
        if (canister.isEmpty()) {
            sprayer.remove(GooDataComponents.SPRAYER_CANISTER.get());
        } else {
            sprayer.set(GooDataComponents.SPRAYER_CANISTER.get(), canister.copy());
        }
    }

    /**
     * Puts a clicked canister in, swaps it for the fitted one, or takes the fitted one out to an
     * empty cursor on a right click; anything else falls through to vanilla.
     *
     * @param sprayer      the sprayer stack in the slot
     * @param cursor       the stack on the cursor
     * @param slot         the inventory slot
     * @param action       the click action
     * @param player       the clicking player
     * @param cursorAccess access to set the cursor contents
     * @return true when the click moved a canister
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack sprayer, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        CanisterClick<ItemStack> click = clickCanister(fittedCanister(sprayer), cursor,
                action == ClickAction.SECONDARY, ITEM_STACKS);
        if (!click.handled()) {
            return false;
        }
        fitCanister(sprayer, click.fitted());
        cursorAccess.set(click.cursor());
        return true;
    }

    /**
     * What the sprayer and the cursor hold after a click, decided apart from the slot: a canister
     * on the cursor goes in and the fitted one, or nothing, comes back to the cursor, which puts
     * one in and swaps alike; a right click with an empty cursor takes the fitted canister out.
     * Anything that is not a canister, a vat included, leaves both as they stood.
     *
     * @param fitted    the canister the sprayer holds, or the empty stack
     * @param cursor    the stack on the cursor
     * @param secondary whether the click is a right click
     * @param stacks    how the click reads a stack
     * @param <S>       the stack type
     * @return what the sprayer and the cursor hold after the click, and whether it moved anything
     */
    static <S> CanisterClick<S> clickCanister(S fitted, S cursor, boolean secondary, ClickedStacks<S> stacks) {
        if (stacks.isCanister(cursor)) {
            return new CanisterClick<>(cursor, fitted, true);
        }
        if (secondary && stacks.isEmpty(cursor) && !stacks.isEmpty(fitted)) {
            return new CanisterClick<>(stacks.empty(), fitted, true);
        }
        return new CanisterClick<>(fitted, cursor, false);
    }

    /**
     * How the click seam reads a stack.
     *
     * @param <S> the stack type
     */
    interface ClickedStacks<S> {
        boolean isEmpty(S stack);

        boolean isCanister(S stack);

        S empty();
    }

    /**
     * What the sprayer and the cursor hold after a click.
     *
     * @param fitted  the canister the sprayer holds, or the empty stack
     * @param cursor  the stack on the cursor
     * @param handled whether the click moved a canister
     * @param <S>     the stack type
     */
    record CanisterClick<S>(S fitted, S cursor, boolean handled) {
    }

    private static final ClickedStacks<ItemStack> ITEM_STACKS = new ClickedStacks<>() {
        @Override
        public boolean isEmpty(ItemStack stack) {
            return stack.isEmpty();
        }

        @Override
        public boolean isCanister(ItemStack stack) {
            return stack.getItem() instanceof CanisterItem;
        }

        @Override
        public ItemStack empty() {
            return ItemStack.EMPTY;
        }
    };
}

package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The sprayer: a gold caulk gun carrying one canister, which inventory clicks put in, take out
 * and swap the way a bundle takes items.
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 * decision sprayer-takes-gives-and-swaps-its-canister-by-clicks
 */
public class SprayerItem extends Item {

    /** The longest hold, a bow's: past any hold a player keeps the use down for. */
    private static final int HOLD_TICKS = 72_000;

    /**
     * Creates the sprayer item.
     *
     * @param properties the item properties
     */
    public SprayerItem(Properties properties) {
        super(properties);
    }

    /**
     * Starts the hold: the sprayer sprays each tick its use stays down, the client sending each tick.
     * decision spray-item-sprays-residuals-beside-the-glove
     *
     * @param level  the level
     * @param player the player using the sprayer
     * @param hand   the hand holding it
     * @return CONSUME, so the use is held
     */
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, Player player, @NonNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /**
     * A hold lasts as long as the use stays down.
     *
     * @param sprayer the sprayer stack
     * @param user    the entity using it
     * @return the longest hold, in ticks
     */
    @Override
    public int getUseDuration(@NonNull ItemStack sprayer, @NonNull LivingEntity user) {
        return HOLD_TICKS;
    }

    /**
     * Keeps the hold while the canister drains: each drip that lands rewrites the canister the sprayer carries, and
     * a hold ended on every rewrite would keep restarting.
     * operator report 2026-11-18: the sprayer kept resetting in the hand while in use
     *
     * @param oldStack the stack the hold began on
     * @param newStack the stack the hand holds now
     * @return true while the hand still holds a sprayer
     */
    @Override
    public boolean canContinueUsing(@NonNull ItemStack oldStack, @NonNull ItemStack newStack) {
        return newStack.getItem() instanceof SprayerItem;
    }

    /**
     * Replays the equip animation only for another item or another slot, never for a canister draining under a
     * spray, as GooItem does for raw goo a cast drains.
     * operator report 2026-11-18: the sprayer kept resetting in the hand while in use
     *
     * @param oldStack    the stack the hand held
     * @param newStack    the stack the hand holds now
     * @param slotChanged whether the selected slot changed
     * @return true where the equip animation replays
     */
    @Override
    public boolean shouldCauseReequipAnimation(@NonNull ItemStack oldStack, @NonNull ItemStack newStack,
            boolean slotChanged) {
        return slotChanged || !(newStack.getItem() instanceof SprayerItem);
    }

    /**
     * The sprayer a player holds, the main hand first.
     *
     * @param player the player
     * @return the held sprayer, or ItemStack.EMPTY where neither hand holds one
     */
    public static ItemStack heldSprayer(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof SprayerItem) {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The canister the sprayer carries, or the empty stack.
     *
     * @param sprayer the sprayer stack
     * @return a copy of the fitted canister, or ItemStack.EMPTY
     */
    public static ItemStack fittedCanister(ItemStack sprayer) {
        ItemContainerContents fitted = sprayer.get(GooDataComponents.SPRAYER_CANISTER.get());
        return fitted == null ? ItemStack.EMPTY : fitted.copyOne();
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
            sprayer.set(GooDataComponents.SPRAYER_CANISTER.get(), ItemContainerContents.fromItems(List.of(canister)));
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
     * The residual the sprayer sprays from its canister's goo: that of the type the canister holds the most of
     * among the types that have one, or none for a canister holding no such type, an empty canister or none.
     * decision spray-item-sprays-residuals-beside-the-glove
     *
     * @param content    the fitted canister's content, empty where the sprayer holds none
     * @param residualOf the residual a goo type has, empty for a type with none
     * @param <A>        the residual's type
     * @return the residual sprayed, or empty
     */
    public static <A> Optional<A> sprayedResidual(CanisterFluidContent content,
            Function<ResourceKey<GooTypeDefinition>, Optional<A>> residualOf) {
        return content.gooVolumesDominantFirst().keySet().stream()
                .map(residualOf).flatMap(Optional::stream).findFirst();
    }

    /**
     * The residual the sprayer sprays: that of the type picked on its wheel where the canister holds it and it has
     * one, else that of the canister's most held type that has one.
     * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
     *
     * @param content    the fitted canister's content, empty where the sprayer holds none
     * @param picked     the type picked on the wheel, empty before any pick
     * @param residualOf the residual a goo type has, empty for a type with none
     * @param <A>        the residual's type
     * @return the residual sprayed, or empty
     */
    public static <A> Optional<A> sprayedResidual(CanisterFluidContent content,
            Optional<ResourceKey<GooTypeDefinition>> picked,
            Function<ResourceKey<GooTypeDefinition>, Optional<A>> residualOf) {
        return picked.filter(type -> content.volumeOf(type) > 0).flatMap(residualOf)
                .or(() -> sprayedResidual(content, residualOf));
    }

    /**
     * What the sprayer's wheel set on it.
     *
     * @param sprayer the sprayer stack
     * @return the setting, the default where nothing was picked
     */
    public static SprayerSetting settingOf(ItemStack sprayer) {
        return sprayer.getOrDefault(GooDataComponents.SPRAYER_SETTING.get(), SprayerSetting.DEFAULT);
    }

    /**
     * Keeps what the sprayer's wheel set on it.
     * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
     *
     * @param sprayer the sprayer stack
     * @param setting the picked type and flow
     */
    public static void keepSetting(ItemStack sprayer, SprayerSetting setting) {
        sprayer.set(GooDataComponents.SPRAYER_SETTING.get(), setting);
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

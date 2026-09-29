package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.GooInteractionType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * Shared interaction infrastructure for slot-based goo blocks.
 * Handles validation, dispatch, and slot search for blocks that
 * accept GooInteractionType-classified item interactions.
 */
public final class GooBlockInteraction {

    /**
     * SuppressWarnings value for unchecked generic casts.
     */
    private static final String UNCHECKED = "unchecked";

    private GooBlockInteraction() {
    }

    /**
     * Full item interaction flow for a machine whose clicks aim at no canister slot:
     * classify -> validate -> dispatch.
     *
     * @param stack      the held item stack
     * @param level      the world
     * @param pos        the block position
     * @param player     the interacting player
     * @param hand       the hand used
     * @param hitResult  the block hit result
     * @param entityType expected block entity class
     * @param rows       the interaction types this machine's dispatcher answers
     * @param dispatcher block-specific dispatch function
     * @param <T>        the block entity type
     * @return the interaction result
     */
    public static <T extends BlockEntity> InteractionResult handleItemInteraction(
            ItemStack stack, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            Class<T> entityType, Set<GooInteractionType> rows,
            Dispatcher<T> dispatcher) {
        return handleItemInteraction(stack, level, pos, player, hand, hitResult,
                entityType, (entity, hit) -> false, rows, dispatcher);
    }

    /**
     * Full item interaction flow: classify against the aimed slot -> validate -> dispatch.
     * Call from useItemOn().
     *
     * @param stack      the held item stack
     * @param level      the world
     * @param pos        the block position
     * @param player     the interacting player
     * @param hand       the hand used
     * @param hitResult  the block hit result
     * @param entityType expected block entity class
     * @param targetSlot reports whether the hit aims at a slot holding a canister
     * @param rows       the interaction types this machine's dispatcher answers
     * @param dispatcher block-specific dispatch function
     * @param <T>        the block entity type
     * @return the interaction result
     */
    public static <T extends BlockEntity> InteractionResult handleItemInteraction(
            ItemStack stack, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            Class<T> entityType, TargetSlot<T> targetSlot,
            Set<GooInteractionType> rows, Dispatcher<T> dispatcher) {
        BlockEntity found = level.getBlockEntity(pos);
        boolean targetSlotFilled = entityType.isInstance(found)
                && targetSlot.holdsCanister(entityType.cast(found), hitResult);
        GooInteractionType interaction = GooInteractionType.classify(stack, targetSlotFilled);
        InteractionResult earlyOut = validate(interaction, level, pos, player, entityType, rows);
        if (earlyOut != null) {
            return earlyOut;
        }

        @SuppressWarnings(UNCHECKED)
        T entity = (T) level.getBlockEntity(pos);
        return dispatcher.dispatch(interaction, entity, stack, player, hand, hitResult, pos, level);
    }

    /**
     * Takes one of the held item on an insert, leaving it in hand for a creative player:
     * the one creative consume rule every machine insert shares
     * (decision every-machine-clicks-through-the-dispatcher).
     *
     * @param stack  the held item stack
     * @param player the interacting player
     */
    public static void consumeOneHeld(ItemStack stack, Player player) {
        stack.consume(1, player);
    }

    /**
     * Shared validation for item interactions.
     * Returns an early-out result or null to continue.
     *
     * @param <T>         the block entity type
     * @param interaction the classified interaction type, or null
     * @param level       the world
     * @param pos         the block position
     * @param player      the interacting player
     * @param entityType  expected block entity class
     * @param rows        the interaction types the machine's dispatcher answers
     * @return an early-out result, or null to continue dispatch
     */
    static <T extends BlockEntity> @Nullable InteractionResult validate(
            @Nullable GooInteractionType interaction, Level level,
            BlockPos pos, Player player, Class<T> entityType,
            Set<GooInteractionType> rows) {
        InteractionResult unanswered = rowGate(interaction, rows);
        if (unanswered != null) {
            return unanswered;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!entityType.isInstance(level.getBlockEntity(pos))) {
            return InteractionResult.PASS;
        }
        return checkCooldown(interaction, level, player);
    }

    /**
     * The answer to a click no row of the machine takes: the tuner and the gasket pass on
     * to their own use, and every other item tries the empty-hand path.
     *
     * @param interaction the classified interaction type, or null
     * @param rows        the interaction types the machine's dispatcher answers
     * @return PASS or TRY_WITH_EMPTY_HAND for a click no row takes, null for one a row takes
     */
    static @Nullable InteractionResult rowGate(@Nullable GooInteractionType interaction,
                                               Set<GooInteractionType> rows) {
        if (interaction != null && rows.contains(interaction)) {
            return null;
        }
        return interaction != null && interaction.passesToItem()
                ? InteractionResult.PASS : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Returns SUCCESS if the interaction is on cooldown, null otherwise.
     *
     * @param interaction the classified interaction type
     * @param level       the current level (for game time)
     * @param player      the interacting player (for UUID-based cooldown)
     * @return SUCCESS if on cooldown, null to continue dispatch
     */
    private static @Nullable InteractionResult checkCooldown(
            GooInteractionType interaction, Level level, Player player) {
        if (interaction.requiresCooldown()
                && InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Shared validation for empty-hand interactions.
     * Returns an early-out result or null to continue.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @return the interaction result
     */
    public static @Nullable InteractionResult validateEmptyHand(
            Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (InteractionCooldown.isOnCooldown(player.getUUID(), level.getGameTime())) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Reports whether a hit on a machine aims at a canister slot that holds a canister.
     *
     * @param <T> the block entity type
     */
    @FunctionalInterface
    public interface TargetSlot<T extends BlockEntity> {
        boolean holdsCanister(T entity, BlockHitResult hitResult);
    }

    /**
     * Dispatches a validated interaction to a block-specific handler.
     *
     * @param <T> the block entity type
     */
    @FunctionalInterface
    public interface Dispatcher<T extends BlockEntity> {
        InteractionResult dispatch(
                GooInteractionType interaction, T entity, ItemStack stack,
                Player player, InteractionHand hand, BlockHitResult hitResult,
                BlockPos pos, Level level);
    }
}

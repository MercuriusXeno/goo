package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver.CanisterPlacement;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.NonNull;
import java.util.Map;

/**
 * Canister item: single-type fluid storage accepting any registered fluid.
 * Capacity scales with Compression enchantment via ContainerCapacity.
 *
 * <p>Overrides placement to support multi-canister blocks: CanisterPlacementResolver
 * decides whether a use inserts into an existing canister block, places a new one,
 * or does nothing.</p>
 */
public class CanisterItem extends BlockItem implements IGooItemInteraction, GooCarrierItem {

    /**
     * Creates a new canister block item.
     *
     * @param block      the canister block
     * @param properties the item properties
     */
    public CanisterItem(Block block, Properties properties) {
        super(block, properties);
    }

    // --- Interaction overrides ---

    /**
     * Places or inserts where the placement resolver says, the rule the green preview also
     * reads (decision preview-runs-the-placement-validator).
     *
     * @param context the use-on context
     * @return the interaction result
     */
    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        Player player = context.getPlayer();
        boolean sneaking = player != null && player.isSecondaryUseActive();
        CanisterPlacement placement = CanisterPlacementResolver.resolve(new BlockPlaceContext(context), sneaking);
        if (placement == null) {
            return InteractionResult.PASS;
        }
        if (placement.intoExisting()) {
            return insertCanister(context, placement);
        }
        InteractionResult result = super.useOn(context);
        shrinkInCreative(result, context, context.getLevel());
        return result;
    }

    /**
     * Shrinks the held item in creative mode after a successful placement.
     *
     * @param result  the placement result
     * @param context the use-on context
     * @param level   the current level
     */
    private static void shrinkInCreative(InteractionResult result, UseOnContext context, Level level) {
        if (result.consumesAction() && !level.isClientSide()
                && context.getPlayer() != null && context.getPlayer().isCreative()) {
            context.getItemInHand().shrink(1);
        }
    }

    /**
     * Inserts this canister into the slot the resolver chose in an existing canister block.
     *
     * @param context   the use-on context
     * @param placement the resolved insert
     * @return the interaction result
     */
    private static InteractionResult insertCanister(UseOnContext context, CanisterPlacement placement) {
        Level level = context.getLevel();
        if (level.isClientSide()) { return InteractionResult.SUCCESS; }
        Player player = context.getPlayer();
        if (player == null
                || !(level.getBlockEntity(placement.pos()) instanceof CanisterBlockEntity canister)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        if (!canister.insertCanister(placement.slot(), stack, player.isCreative())) {
            return InteractionResult.PASS;
        }
        stack.shrink(1);
        canister.playInsertSound();
        return InteractionResult.SUCCESS;
    }

    // --- Block placement ---

    /**
     * After normal block placement, reads goo data from the held ItemStack and
     * assigns it to the target slot directly. We cannot rely on pendingGooContents
     * because applyImplicitComponents runs later in BlockItem.place(), after
     * placeBlock() has already returned.
     *
     * @param context the block placement context
     * @param state   the block state to place
     * @return true if the block was placed
     */
    @Override
    protected boolean placeBlock(@NonNull BlockPlaceContext context, @NonNull BlockState state) {
        if (!super.placeBlock(context, state)) { return false; }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof CanisterBlockEntity be) {
            initPlacedCanister(context, level, pos, be);
        }
        return true;
    }

    /**
     * Assigns goo data, owner, and gasket cleanup after block placement.
     *
     * @param context the block placement context
     * @param level   the current level
     * @param pos     the canister block position
     * @param be      the canister block entity
     */
    private void initPlacedCanister(
            BlockPlaceContext context, Level level, BlockPos pos, CanisterBlockEntity be) {
        int slot = CanisterPlacementResolver.newBlockSlot(
                level, pos, context.getClickLocation(), context.getClickedFace());
        boolean creative = context.getPlayer() != null && context.getPlayer().isCreative();
        be.assignFromItemStack(slot, context.getItemInHand(), creative);
        CanisterPlacementValidator.stampOwner(be, context.getPlayer());
        CanisterInventoryHandler.popConflictingGaskets(level, pos);
    }

    // --- GooCarrierItem (decision hosts-answer-bounds-through-interfaces) ---

    @Override
    public DepletionPass depletionPass() {
        return DepletionPass.CANISTER;
    }

    @Override
    public Map<ResourceKey<GooTypeDefinition>, Integer> gooContents(ItemStack stack) {
        return gooContentsOf(stack);
    }

    @Override
    public int drawGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return removeGoo(stack, type, amount);
    }

    /**
     * The goo a canister stack holds, which a hub item's carried canisters also read.
     *
     * @param stack a canister stack
     * @return its goo type with the volume, or an empty map when it holds no goo
     */
    static Map<ResourceKey<GooTypeDefinition>, Integer> gooContentsOf(ItemStack stack) {
        CanisterFluidContent content = getFluidContent(stack);
        ResourceKey<GooTypeDefinition> type = content.getGooType();
        return type != null && content.amount() > 0 ? Map.of(type, content.amount()) : Map.of();
    }

    // --- Static contents helpers ---

    /**
     * Returns the fluid content from the stack, or EMPTY if none.
     *
     * @param stack the item stack
     * @return the fluid content, never null
     */
    public static CanisterFluidContent getFluidContent(ItemStack stack) {
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        return content != null ? content : CanisterFluidContent.EMPTY;
    }

    /**
     * Returns the canister metadata from the stack, or EMPTY if none. Gasket UUIDs
     * are not auto-generated; they are only created when a choral gasket is
     * physically installed.
     *
     * @param stack the item stack
     * @return the canister metadata, never null
     */
    public static CanisterMetadata getMetadata(ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        return meta != null ? meta : CanisterMetadata.EMPTY;
    }

    /**
     * Sets the fluid content on the stack. Removes component if empty.
     *
     * @param stack   the item stack
     * @param content the fluid content to set
     */
    public static void setFluidContent(ItemStack stack, CanisterFluidContent content) {
        if (content.isEmpty()) {
            stack.remove(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        } else {
            stack.set(GooDataComponents.CANISTER_FLUID_CONTENT.get(), content);
        }
    }

    /**
     * Sets the canister metadata on the stack. Removes component if no data.
     *
     * @param stack the item stack
     * @param meta  the canister metadata to set
     */
    public static void setMetadata(ItemStack stack, CanisterMetadata meta) {
        if (meta.hasData()) {
            stack.set(GooDataComponents.CANISTER_METADATA.get(), meta);
        } else {
            stack.remove(GooDataComponents.CANISTER_METADATA.get());
        }
    }

    /**
     * Try to add fluid to the canister. Only accepts if empty or same fluid.
     * Returns the amount actually added.
     *
     * @param stack  the canister item stack
     * @param fluid  the fluid resource to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static int addFluid(ItemStack stack, FluidResource fluid, int amount) {
        int capacity = ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack));
        CanisterFluidContent current = getFluidContent(stack);
        int accepted = current.cappedAddAmount(fluid, amount, capacity);
        if (accepted > 0) {
            setFluidContent(stack, current.withCappedAdd(fluid, amount, capacity));
        }
        return accepted;
    }

    /**
     * Convenience: add goo by type. Resolves goo type key to its stamped resource.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to add
     * @param amount the volume in microblobs to add
     * @return the amount actually accepted
     */
    public static int addGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return addFluid(stack, GooFluids.resource(type), amount);
    }

    /**
     * Try to remove fluid from the canister. Only extracts if the canister
     * holds the specified fluid. Returns the amount actually removed.
     *
     * @param stack  the canister item stack
     * @param fluid  the fluid resource to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static int removeFluid(ItemStack stack, FluidResource fluid, int amount) {
        CanisterFluidContent current = getFluidContent(stack);
        if (current.isEmpty() || !current.resource().equals(fluid)) { return 0; }
        int removed = Math.min(amount, current.amount());
        setFluidContent(stack, current.withRemoved(removed));
        return removed;
    }

    /**
     * Convenience: remove goo by type. Resolves goo type key to its stamped resource.
     *
     * @param stack  the canister item stack
     * @param type   the goo type to remove
     * @param amount the volume in microblobs to remove
     * @return the amount actually removed
     */
    public static int removeGoo(ItemStack stack, ResourceKey<GooTypeDefinition> type, int amount) {
        return removeFluid(stack, GooFluids.resource(type), amount);
    }

    // --- Inventory click interactions ---

    /**
     * Handles cursor-on-canister inventory clicks: blob/omniblob insert,
     * empty-cursor drain.
     *
     * @param canister    the canister item stack in the slot
     * @param cursor      the item stack on the cursor
     * @param slot        the inventory slot
     * @param action      the click action (primary or secondary)
     * @param player      the interacting player
     * @param cursorAccess access to set the cursor contents
     * @return true if the interaction was handled
     */
    @Override
    public boolean overrideOtherStackedOnMe(@NonNull ItemStack canister, @NonNull ItemStack cursor,
            @NonNull Slot slot, @NonNull ClickAction action, @NonNull Player player,
            @NonNull SlotAccess cursorAccess) {
        if (cursor.isEmpty() && action == ClickAction.SECONDARY) {
            return CanisterInventoryHandler.drainIntoInventory(new CanisterGooSource(canister),
                    GooDeposit.intoInventory(player, canister));
        }
        return action == ClickAction.PRIMARY
                && CanisterInventoryHandler.handlePrimaryClick(canister, cursor, cursorAccess);
    }

    /**
     * Returns CANISTER_INSERT so canister blocks route to slot insertion logic.
     *
     * @return the canister insert interaction type
     */
    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.CANISTER_INSERT;
    }

    /**
     * The canister item as a drain source: its one goo type, whole.
     *
     * @param canister the canister item stack
     */
    private record CanisterGooSource(ItemStack canister) implements CanisterInventoryHandler.GooSource {
        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> drainable() {
            CanisterFluidContent content = getFluidContent(canister);
            ResourceKey<GooTypeDefinition> type = content.getGooType();
            return content.isEmpty() || type == null ? Map.of() : Map.of(type, content.amount());
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int volume) {
            return removeGoo(canister, type, volume);
        }
    }
}

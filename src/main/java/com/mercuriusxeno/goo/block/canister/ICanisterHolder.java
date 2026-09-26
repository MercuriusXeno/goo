package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.IGooLightSource;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Common interface for block entities that hold canister slots with metadata
 * and support fluid insert/extract operations. Both {@link CanisterBlockEntity}
 * and {@link HubBlockEntity} store canister item stacks in numbered slots.
 * This interface lets callers operate on either type without type-branching.
 *
 * <p>All slot operations route through {@link #slot(int)} which returns the
 * underlying {@link CanisterSlot}. Per-slot work is on the slot itself; this
 * interface only adds bounds checks and item-stack metadata convenience.</p>
 */
public interface ICanisterHolder extends IGooLightSource {

    /**
     * @return the behavioral component owning this holder's slot grid
     */
    SlottedCanisterData containerState();

    /**
     * Bounds-checked slot accessor.
     *
     * @param index the slot index
     * @return the slot, or null if {@code index} is out of range
     */
    default @Nullable CanisterSlot slot(int index) {
        SlottedCanisterData state = containerState();
        return state.inRange(index) ? state.slots[index] : null;
    }

    /**
     * @param index the slot index
     * @return the canister stack at the given slot, or EMPTY when out of range or empty
     */
    default ItemStack getCanister(int index) {
        CanisterSlot s = slot(index);
        return s != null ? s.canister() : ItemStack.EMPTY;
    }

    /**
     * @param index the slot index
     * @return the slot's fluid content, or {@link CanisterFluidContent#EMPTY}
     */
    default CanisterFluidContent getSlotFluidContent(int index) {
        CanisterSlot s = slot(index);
        return s != null ? s.fluidContent() : CanisterFluidContent.EMPTY;
    }

    /**
     * @param index the slot index
     * @return the goo type the slot's canister holds, or null when it holds
     *         no goo (empty, out of range, or a fluid carrying no goo type)
     */
    default @Nullable ResourceKey<GooTypeDefinition> getSlotGooType(int index) {
        return getSlotFluidContent(index).getGooType();
    }

    /**
     * @param index the slot index
     * @return metadata on the canister stack, or {@link CanisterMetadata#EMPTY}
     */
    default CanisterMetadata getSlotMetadata(int index) {
        ItemStack stack = getCanister(index);
        return stack.isEmpty() ? CanisterMetadata.EMPTY : CanisterItem.getMetadata(stack);
    }

    /**
     * Updates the canister metadata at the given slot and triggers a sync.
     * No-op if the slot is empty or out of range.
     *
     * @param index    the slot index
     * @param metadata the new metadata to apply
     */
    default void setSlotMetadata(int index, CanisterMetadata metadata) {
        CanisterSlot s = slot(index);
        if (s == null || s.isEmpty()) {
            return;
        }
        s.setMetadata(metadata);
    }

    /**
     * @param index the slot index
     * @return true if the slot has a canister with remaining capacity
     */
    default boolean canAccept(int index) {
        CanisterSlot s = slot(index);
        return s != null && s.canAccept();
    }

    /**
     * Inserts fluid into the slot's canister.
     *
     * @param index  the slot index
     * @param fluid  the fluid resource to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    default int insertFluid(int index, FluidResource fluid, int volume) {
        CanisterSlot s = slot(index);
        return s != null ? s.insertFluid(fluid, volume) : 0;
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param index        the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the amount actually inserted
     */
    default int insertGoo(int index, ResourceKey<GooTypeDefinition> incomingType, int volume) {
        return insertFluid(index, GooFluids.resource(incomingType), volume);
    }

    /**
     * Extracts fluid from the slot's canister.
     *
     * @param index     the slot index
     * @param fluid     the fluid resource to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    default int extractFluid(int index, FluidResource fluid, int requested) {
        CanisterSlot s = slot(index);
        return s != null ? s.extractFluid(fluid, requested) : 0;
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param index     the slot index
     * @param type      the goo type to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    default int extractGoo(int index, ResourceKey<GooTypeDefinition> type, int requested) {
        return extractFluid(index, GooFluids.resource(type), requested);
    }

    // --- Client-read geometry (decision hosts-answer-bounds-through-interfaces) ---

    /**
     * @param index the slot index
     * @return true when the slot holds a canister
     */
    default boolean isSlotFilled(int index) {
        return !getCanister(index).isEmpty();
    }

    /**
     * The block-local bounds a slot's canister occupies, filled or not.
     *
     * @param index the slot index
     * @return the slot's bounds, or null when {@code index} is no slot of this holder
     */
    @Nullable AABB slotBounds(int index);

    /**
     * The outline the block draws while the cursor rests on it.
     *
     * @param hit the ray trace hit on this holder
     * @return the outline shape in block-local coordinates
     */
    VoxelShape outlineShape(BlockHitResult hit);

    /**
     * The filled slot an empty-hand click at the hit would take the canister from.
     *
     * @param hit the ray trace hit on this holder
     * @return the slot's bounds, or null when the hit addresses no filled slot
     */
    @Nullable AABB pickupBounds(BlockHitResult hit);

    /**
     * The empty slot a held canister would enter on a click at the hit.
     *
     * @param hit      the ray trace hit on this holder
     * @param sneaking true when the player is sneaking
     * @return the slot's bounds, or null when the click would insert nowhere
     */
    @Nullable AABB previewBounds(BlockHitResult hit, boolean sneaking);

    /**
     * The HUD target under the cursor: the slot it reads and where its panel sits.
     *
     * @param hit    the ray trace hit on this holder
     * @param viewer what the client knows about the viewer
     * @return the anchor, or null when the hit reads no canister
     */
    @Nullable HudAnchor hudAnchor(BlockHitResult hit, HudViewer viewer);

    /**
     * Whether a canister use at the hit goes into this holder rather than
     * placing a new canister block beside it.
     *
     * @param hit      the ray trace hit on this holder
     * @param sneaking true when the player is sneaking
     * @return true when this holder takes the use
     */
    boolean takesCanisterAt(BlockHitResult hit, boolean sneaking);

    /**
     * The empty slot a canister placed against a neighbour would enter here,
     * this holder standing where the new block would go. Default: none.
     *
     * @param hitLocation the world-space point on the neighbour's face
     * @return the slot, or {@code NO_SLOT} when this holder takes no such insertion
     */
    default int insertionSlotFrom(Vec3 hitLocation) {
        return NO_SLOT;
    }
}

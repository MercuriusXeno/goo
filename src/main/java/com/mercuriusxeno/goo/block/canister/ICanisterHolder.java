package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.GooLightContribution;
import com.mercuriusxeno.goo.block.IGooLightSource;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

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
     * The level this holder sits in, which every implementing block entity
     * already answers; null until the block entity is placed in one.
     *
     * @return the level, or null before placement
     */
    @Nullable Level getLevel();

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

    /**
     * Default impl walks every slot, computes each slot's emission via
     * {@link GooLightContribution#forSlot} over the type's registry entry,
     * and returns the sum clamped to the vanilla 15-light ceiling.
     * Sufficient for canister, hub, tap, and reactor BEs. Before placement
     * no registry is reachable, so the emission reads 0.
     *
     * @return total goo-derived block-light emission in [0, 15]
     */
    @Override
    default int gooLightEmission() {
        Level level = getLevel();
        if (level == null) {
            return 0;
        }
        HolderLookup.Provider registries = level.registryAccess();
        SlottedCanisterData data = containerState();
        int total = 0;
        for (CanisterSlot slot : data.slots) {
            CanisterFluidContent content = slot.fluidContent();
            ResourceKey<GooTypeDefinition> type = content.getGooType();
            if (content.isEmpty() || type == null) {
                continue;
            }
            int contribution = GooLightContribution.forSlot(
                    GooTypes.definition(registries, type), content.amount(), slot.capacity());
            total = GooLightContribution.addClamped(total, contribution);
            if (total >= GooLightContribution.MAX_LIGHT) {
                return GooLightContribution.MAX_LIGHT;
            }
        }
        return total;
    }
}

package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.CanisterSlotFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.OptionalInt;

/**
 * Block-level fluid handler for the Hub. Presents one virtual tank per
 * canister slot (up to {@link HubBlockEntity#MAX_CANISTERS}). Each tank
 * reflects the single-type fluid in the corresponding canister.
 *
 * <p>This is a read-through/write-through adapter: no data duplication.
 * All state lives on the Hub's internal canister ItemStacks.</p>
 */
public class HubFluidHandler implements ResourceHandler<FluidResource>, GasketDemand {

    private final HubBlockEntity hub;

    /**
     * Creates a handler backed by the given hub.
     *
     * @param hub the hub block entity
     */
    public HubFluidHandler(HubBlockEntity hub) {
        this.hub = hub;
    }

    /**
     * Returns one tank per canister slot.
     *
     * @return the tank count
     */
    @Override
    public int size() {
        return HubBlockEntity.MAX_CANISTERS;
    }

    /**
     * Returns the fluid in the canister at the given slot.
     *
     * @param index the tank index (canister slot)
     * @return the fluid resource
     */
    @Override
    public FluidResource getResource(int index) {
        CanisterFluidContent content = getSlotContent(index);
        return content.isEmpty()
                ? FluidResource.EMPTY
                : content.resource();
    }

    /**
     * Returns the volume in the canister at the given slot.
     *
     * @param index the tank index
     * @return the amount
     */
    @Override
    public long getAmountAsLong(int index) {
        return getSlotContent(index).amount();
    }

    /**
     * Returns the capacity of the canister at the given slot.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the capacity
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        ItemStack stack = hub.getCanister(index);
        if (stack.isEmpty()) {
            return 0;
        }
        return ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack));
    }

    /**
     * Accepts any fluid if the canister slot is empty or already holds it.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ItemStack stack = hub.getCanister(index);
        if (stack.isEmpty()) {
            return false;
        }
        CanisterFluidContent content = CanisterItem.getFluidContent(stack);
        return content.isEmpty() || content.resource().equals(resource);
    }

    /**
     * Inserts fluid by routing to matching or empty canister slots.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume
     * @param transaction the transaction context
     * @return the amount inserted
     */
    @Override
    public int insert(int index, FluidResource resource, int amount,
                      TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        int shared = shareByDemand(resource, amount, transaction);
        return shared + hub.containerState().routeFluid(resource, amount - shared, transaction);
    }

    /**
     * Gives each canister taking the fluid up to the demand it states, so one canister's
     * share never pours into another that merely sits first (decision receivers-demand-and-links-relay).
     *
     * @param resource    the fluid arriving
     * @param amount      the mB arriving
     * @param transaction the caller's transaction
     * @return the mB the canisters took by demand
     */
    private int shareByDemand(FluidResource resource, int amount, TransactionContext transaction) {
        int left = amount;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS && left > 0; i++) {
            CanisterSlotFluidHandler slot = hub.containerState().getSlotFluidHandler(i);
            if (slot != null && slot.isValid(0, resource)) {
                left -= slot.insert(0, resource, Math.min(left, GasketDemand.demandOf(slot, resource)), transaction);
            }
        }
        return amount - left;
    }

    /**
     * Extracts fluid by scanning canister slots for the requested type.
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      volume
     * @param transaction the transaction context
     * @return the amount extracted
     */
    @Override
    public int extract(int index, FluidResource resource, int amount,
                       TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        return extractFromCanisters(resource, amount);
    }

    /**
     * Scans hub canisters and extracts the requested fluid.
     *
     * @param fluid  the fluid resource to extract
     * @param amount the maximum amount to extract in mB
     * @return the total amount actually extracted
     */
    private int extractFromCanisters(FluidResource fluid, int amount) {
        int remaining = scanAndExtract(fluid, amount);
        int totalExtracted = amount - remaining;
        if (totalExtracted > 0) {
            BlockEntitySync.markDirtyAndSync(hub);
        }
        return totalExtracted;
    }

    /**
     * Iterates hub canister slots and removes fluid of the requested type.
     *
     * @param fluid  the fluid resource to remove from canisters
     * @param amount the maximum amount to remove in mB
     * @return the remaining amount that could not be extracted
     */
    private int scanAndExtract(FluidResource fluid, int amount) {
        int remaining = amount;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS && remaining > 0; i++) {
            ItemStack stack = hub.getCanister(i);
            if (stack.isEmpty()) {
                continue;
            }
            remaining -= CanisterItem.removeFluid(stack, fluid, remaining);
        }
        return remaining;
    }

    /**
     * The hub's intake asks what its canisters ask together, each its resting demand at the
     * power law of its capacity plus its own consumer's (decision relay-adds-dependent-ask-to-own).
     */
    @Override
    public OptionalInt statedDemand(FluidResource resource) {
        long total = 0;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            CanisterSlotFluidHandler slot = hub.containerState().getSlotFluidHandler(i);
            if (slot != null && slot.isValid(0, resource)) {
                total += GasketDemand.demandOf(slot, resource);
            }
        }
        return OptionalInt.of((int) Math.min(total, Integer.MAX_VALUE));
    }

    // --- Helpers ---

    /**
     * Reads the fluid content from the canister at the given slot.
     *
     * @param index the hub canister slot index to read
     * @return the fluid content of that slot, or EMPTY if vacant
     */
    private CanisterFluidContent getSlotContent(int index) {
        ItemStack stack = hub.getCanister(index);
        return stack.isEmpty() ? CanisterFluidContent.EMPTY : CanisterItem.getFluidContent(stack);
    }
}

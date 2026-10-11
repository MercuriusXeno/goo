package com.mercuriusxeno.goo.block.hub;

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
 * shows the fluid its canister holds most of; inserts and extracts route by
 * resource, so a canister holding several goo types gives up each alone.
 *
 * <p>This is a read-through/write-through adapter: no data duplication.
 * Reads come from the hub's canister stacks; writes go through each slot's
 * fluid handler, which writes back onto its stack.</p>
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
        return getSlotContent(index).dominantResource();
    }

    /**
     * Returns the volume of the fluid the canister at the given slot holds most of.
     *
     * @param index the tank index
     * @return the amount
     */
    @Override
    public long getAmountAsLong(int index) {
        CanisterFluidContent.Portion dominant = getSlotContent(index).dominant();
        return dominant == null ? 0 : dominant.amount();
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
     * Accepts any fluid the canister at the slot takes: goo beside goo, a
     * vanilla fluid alone (decision canisters-hold-more-than-one-goo-type).
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        ItemStack stack = hub.getCanister(index);
        return !stack.isEmpty() && CanisterItem.getFluidContent(stack).canAccept(resource);
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
        return amount - scanAndExtract(resource, amount, transaction);
    }

    /**
     * Iterates hub canister slots and removes fluid of the requested type through
     * each slot's handler inside the caller's transaction; on commit each handler
     * writes its change back onto its canister stack and syncs the hub.
     *
     * @param fluid       the fluid resource to remove from canisters
     * @param amount      the maximum amount to remove in mB
     * @param transaction the caller's transaction
     * @return the remaining amount that could not be extracted
     */
    private int scanAndExtract(FluidResource fluid, int amount, TransactionContext transaction) {
        int remaining = amount;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS && remaining > 0; i++) {
            CanisterSlotFluidHandler slot = hub.containerState().getSlotFluidHandler(i);
            if (slot != null) {
                remaining -= slot.extract(0, fluid, remaining, transaction);
            }
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

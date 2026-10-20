package com.mercuriusxeno.goo.item.fluid;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import java.util.function.UnaryOperator;

/**
 * Item fluid handler for canisters: one tank per fluid held, in arrival order,
 * plus one empty tank for a fluid not yet held, every tank on the one shared
 * capacity the held void grows. An insert or extract finds its fluid's tank by
 * resource, whichever index the caller names.
 *
 * decision canisters-hold-more-than-one-goo-type
 * decision each-void-mb-adds-n-plus-one-capacity
 */
public class CanisterFluidHandler implements ResourceHandler<FluidResource> {

    private final ItemAccess itemAccess;

    /**
     * Creates a handler wrapping the given canister's ItemAccess.
     *
     * @param itemAccess the item access for the canister stack
     */
    public CanisterFluidHandler(ItemAccess itemAccess) {
        this.itemAccess = itemAccess;
    }

    @Override
    public int size() {
        return readContent(itemAccess.getResource()).portions().size() + 1;
    }

    @Override
    public FluidResource getResource(int index) {
        var portions = readContent(itemAccess.getResource()).portions();
        return index >= 0 && index < portions.size() ? portions.get(index).resource() : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        var portions = readContent(itemAccess.getResource()).portions();
        return index >= 0 && index < portions.size() ? portions.get(index).amount() : 0;
    }

    /**
     * The room the resource has, counting this tank's own volume.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the room this tank has in mB, counting its own volume
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        CanisterFluidContent content = readContent(itemAccess.getResource());
        return (long) content.roomFor(resource, ContainerCapacity.CANISTER_BASE) + getAmountAsLong(index);
    }

    /**
     * Any fluid the held content accepts: goo beside goo, a vanilla fluid alone.
     *
     * @param index    the tank index, which routing ignores
     * @param resource the fluid resource to validate
     * @return true if the fluid can be inserted
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        ItemResource item = itemAccess.getResource();
        return readContent(item).canAccept(resource, readGooLabel(item));
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        ItemResource item = itemAccess.getResource();
        int count = itemAccess.getAmount();
        if (count == 0) {
            return 0;
        }
        int base = ContainerCapacity.CANISTER_BASE;
        ResourceKey<GooTypeDefinition> label = readGooLabel(item);
        int perItem = readContent(item).cappedAddAmount(resource, amount / count, base, label);
        return exchange(item, count, perItem, content -> content.withCappedAdd(resource, perItem, base, label),
                transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        ItemResource item = itemAccess.getResource();
        int count = itemAccess.getAmount();
        if (count == 0) {
            return 0;
        }
        int perItem = Math.min(amount / count, readContent(item).releasableVolumeOf(resource, ContainerCapacity.CANISTER_BASE));
        return exchange(item, count, perItem, content -> content.withRemoved(resource, perItem), transaction);
    }

    /**
     * Swaps every canister in the access for one carrying the changed content.
     *
     * @param item        the canister as it stands
     * @param count       how many canisters the access holds
     * @param perItem     the mB moved per canister
     * @param change      the content change each canister takes
     * @param transaction the caller's transaction
     * @return the mB moved across every canister, or 0 when the swap is refused
     */
    private int exchange(ItemResource item, int count, int perItem,
                         UnaryOperator<CanisterFluidContent> change, TransactionContext transaction) {
        if (perItem <= 0) {
            return 0;
        }
        ItemResource changed = writeContent(item, change.apply(readContent(item)));
        return itemAccess.exchange(changed, count, transaction) == count ? perItem * count : 0;
    }

    // --- Helpers ---

    /**
     * Reads canister fluid content from the item, defaulting to EMPTY.
     * @param item the item resource to read fluid data from
     * @return the stored fluid content, or EMPTY if none
     */
    private static CanisterFluidContent readContent(ItemResource item) {
        CanisterFluidContent content = item.getComponents().get(
            GooDataComponents.CANISTER_FLUID_CONTENT.get());
        return content != null ? content : CanisterFluidContent.EMPTY;
    }

    /**
     * Reads the goo label type from the canister's metadata.
     * @param item the item resource to read
     * @return the goo label type, or null where the canister carries none
     */
    private static @Nullable ResourceKey<GooTypeDefinition> readGooLabel(ItemResource item) {
        CanisterMetadata meta = item.getComponents().get(GooDataComponents.CANISTER_METADATA.get());
        return meta != null ? meta.gooLabel() : null;
    }

    /**
     * Writes canister fluid content onto the item, dropping the component when empty.
     * @param item    the item resource to update
     * @param content the content to write
     * @return the updated item resource
     */
    private static ItemResource writeContent(ItemResource item, CanisterFluidContent content) {
        return content.isEmpty()
            ? item.without(GooDataComponents.CANISTER_FLUID_CONTENT)
            : item.with(GooDataComponents.CANISTER_FLUID_CONTENT, content);
    }
}

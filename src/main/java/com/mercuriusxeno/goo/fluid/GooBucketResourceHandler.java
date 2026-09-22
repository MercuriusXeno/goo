package com.mercuriusxeno.goo.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The fluid handler of a goo bucket. NeoForge's bucket handler answers the
 * bucket's bare fluid, which for the generic goo fluid names no type, so
 * this one answers the resource stamped with the type the bucket stack
 * carries (decisions generic-goo-fluids and generic-goo-items): a canister
 * drained from the bucket, and the bucket's item tint, both read the type
 * from it.
 */
public class GooBucketResourceHandler extends ItemAccessResourceHandler<FluidResource> {

    /**
     * @param itemAccess the item access for the bucket stack
     */
    public GooBucketResourceHandler(ItemAccess itemAccess) {
        super(itemAccess, 1);
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource item, int index) {
        ResourceKey<GooTypeDefinition> key = item.getItem() instanceof GooBucketItem
                ? item.getComponents().get(GooDataComponents.GOO_TYPE.get())
                : null;
        return key == null ? FluidResource.EMPTY : GooFluids.resource(key);
    }

    @Override
    protected int getAmountFrom(ItemResource item, int index) {
        return getResourceFrom(item, index).isEmpty() ? 0 : FluidType.BUCKET_VOLUME;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FluidType.BUCKET_VOLUME;
    }

    /**
     * An emptied bucket becomes the plain bucket; a filled one becomes the
     * bucket the fluid type names for the resource, which for stamped goo is
     * the bucket of that type.
     *
     * @param item      the bucket item resource before the transfer
     * @param oldAmount the amount held before
     * @param resource  the fluid resource transferred
     * @param newAmount the amount held after
     * @return the bucket item resource after the transfer
     */
    @Override
    protected ItemResource update(ItemResource item, int oldAmount, FluidResource resource, int newAmount) {
        if (newAmount == 0) {
            return ItemResource.of(Items.BUCKET);
        }
        return ItemResource.of(resource.getFluid().getFluidType().getBucket(resource.toStack(newAmount)));
    }
}

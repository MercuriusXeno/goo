package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.function.ToIntFunction;

/**
 * The crystallizer's holding: one goo tank per type, each taking no more than
 * the phase in progress needs (decision crystallizer-emits-chrysm), so a gasket
 * link pours in only the forming type and crystal and a third type stays put.
 */
final class CrystallizerTank extends GooFluidHandler {

    private final ToIntFunction<ResourceKey<GooTypeDefinition>> capacityFor;

    /**
     * @param onChange    called when contents change
     * @param capacityFor the most of a type the holding takes now
     */
    CrystallizerTank(Runnable onChange, ToIntFunction<ResourceKey<GooTypeDefinition>> capacityFor) {
        super(Integer.MAX_VALUE, onChange);
        this.capacityFor = capacityFor;
    }

    /** A tank takes its own type while the phase in progress still needs it. */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return super.isValid(index, resource) && capacityFor.applyAsInt(GooTypes.order().get(index)) > 0;
    }

    /** A tank's capacity is what the phase needs, never below what it already holds. */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return Math.max(capacityFor.applyAsInt(GooTypes.order().get(index)), getAmountAsInt(index));
    }
}

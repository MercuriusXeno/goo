package com.mercuriusxeno.goo.block.gasket;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.OptionalInt;

/**
 * The rate a receiver on the gasket network asks of its partner each tick
 * (decision receivers-demand-and-links-relay): a receiver's fluid handler
 * implements this to state a demand of its own, and a handler that states
 * none, or does not implement it, asks the power-law rate so a container with
 * no consumer behind it fills as it always has.
 */
@FunctionalInterface
public interface GasketDemand {

    /**
     * The mB this receiver asks per tick of the given resource.
     *
     * @param resource the fluid its partner would send
     * @return the demand, or empty to ask the power-law default
     */
    OptionalInt statedDemand(FluidResource resource);

    /**
     * The power-law rate a receiver stating no demand asks: the taper rate of
     * what its source holds, at the fluid's own exponent.
     *
     * @param resource    the fluid the source holds
     * @param sourceHolds the mB the source holds
     * @return the default demand, in mB per tick
     */
    static int defaultDemand(FluidResource resource, int sourceHolds) {
        return GasketPushMath.taperRate(sourceHolds, GasketPushMath.exponentFor(resource.getFluid()));
    }

    /**
     * The demand a receiver answers its source this tick: the one it states, or the default.
     *
     * @param receiver    the receiver's fluid handler
     * @param resource    the fluid the source would send
     * @param sourceHolds the mB the source holds
     * @return the demand, in mB per tick
     */
    static int demandOf(ResourceHandler<FluidResource> receiver, FluidResource resource, int sourceHolds) {
        return statedDemandOf(receiver, resource).orElseGet(() -> defaultDemand(resource, sourceHolds));
    }

    /**
     * The demand a receiver states of its own, the one a link relays upstream.
     *
     * @param receiver the receiver's fluid handler, or null when there is none
     * @param resource the fluid it would receive
     * @return its stated demand, or empty when it states none
     */
    static OptionalInt statedDemandOf(@Nullable ResourceHandler<FluidResource> receiver, FluidResource resource) {
        return receiver instanceof GasketDemand demanding ? demanding.statedDemand(resource) : OptionalInt.empty();
    }
}

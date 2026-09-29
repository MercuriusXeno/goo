package com.mercuriusxeno.goo.block.gasket;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.OptionalInt;

/**
 * The rate a receiver on the gasket network asks of its partner each tick
 * (decision receivers-demand-and-links-relay): a link adds the demand placed
 * on it from behind, and at rest asks the power law of its own capacity, so the
 * bigger a container is the stronger it pulls.
 */
@FunctionalInterface
public interface GasketDemand {

    /**
     * The mB this receiver asks per tick of the given resource.
     *
     * @param resource the fluid its partner would send
     * @return the demand, or empty when it cannot say, which asks its resting demand
     */
    OptionalInt statedDemand(FluidResource resource);

    /**
     * The demand a container asks at rest: the taper rate of its capacity at the
     * fluid's own exponent, no more than the room it has left.
     *
     * @param resource the fluid it would receive
     * @param capacity the most it holds, in mB
     * @param held     what it holds now, in mB
     * @return the resting demand, in mB per tick
     */
    static int restingDemand(FluidResource resource, long capacity, long held) {
        int pull = GasketPushMath.taperRate((int) Math.min(capacity, Integer.MAX_VALUE),
                GasketPushMath.exponentFor(resource.getFluid()));
        return (int) Math.max(0, Math.min(pull, capacity - held));
    }

    /**
     * A link's demand: its own resting demand plus the demand placed on it from behind
     * (decision relay-adds-dependent-ask-to-own).
     *
     * @param placed  the demand of what stands behind the link, or empty when nothing does
     * @param resting the link's resting demand
     * @return the demand the link states upstream
     */
    static int stackOnRest(OptionalInt placed, int resting) {
        long stacked = (long) resting + Math.max(0, placed.orElse(0));
        return (int) Math.min(stacked, Integer.MAX_VALUE);
    }

    /**
     * The demand a receiver answers its source this tick: the one it states, or for a
     * handler stating none, the resting demand of its largest tank that takes the resource.
     *
     * @param receiver the receiver's fluid handler
     * @param resource the fluid the source would send
     * @return the demand, in mB per tick
     */
    static int demandOf(ResourceHandler<FluidResource> receiver, FluidResource resource) {
        return statedDemandOf(receiver, resource).orElseGet(() -> restingDemandOf(receiver, resource));
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

    /**
     * The resting demand of a handler that states none: that of its largest tank taking the resource.
     *
     * @param receiver the receiver's fluid handler
     * @param resource the fluid it would receive
     * @return the resting demand, in mB per tick
     */
    private static int restingDemandOf(ResourceHandler<FluidResource> receiver, FluidResource resource) {
        int best = 0;
        for (int i = 0; i < receiver.size(); i++) {
            if (receiver.isValid(i, resource)) {
                best = Math.max(best, restingDemand(resource, receiver.getCapacityAsLong(i, resource),
                        receiver.getAmountAsLong(i)));
            }
        }
        return best;
    }
}

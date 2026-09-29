package com.mercuriusxeno.goo.block.gasket;

import net.neoforged.neoforge.transfer.fluid.FluidResource;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * A container's link in the gasket chain (decision receivers-demand-and-links-relay):
 * it mirrors the demand placed on it from behind, and at rest asks its own resting
 * demand. A loop of links asking round finds this link already answering and rests.
 */
public final class DemandRelay {

    private Function<FluidResource, OptionalInt> placed = resource -> OptionalInt.empty();
    private boolean answering;

    /**
     * Sets where the link reads the demand placed on it from behind.
     *
     * @param demandBehind the stated demand of what stands behind the link, or empty when nothing does
     */
    public void readDemandFrom(Function<FluidResource, OptionalInt> demandBehind) {
        this.placed = demandBehind;
    }

    /**
     * The demand the link states upstream.
     *
     * @param resource the fluid it would receive
     * @param resting  its resting demand
     * @return the demand, or empty while this link is already answering further down the same ask
     */
    public OptionalInt statedDemand(FluidResource resource, IntSupplier resting) {
        if (answering) {
            return OptionalInt.empty();
        }
        answering = true;
        try {
            return OptionalInt.of(GasketDemand.mirrorOrRest(placed.apply(resource), resting.getAsInt()));
        } finally {
            answering = false;
        }
    }
}

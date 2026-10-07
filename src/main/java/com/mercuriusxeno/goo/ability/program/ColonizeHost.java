package com.mercuriusxeno.goo.ability.program;

/**
 * A host where a landing blob can grow a shroom network (capability
 * {@link HostCapability#COLONIZE}): the network of the block it landed on
 * spreads, and a landing on no network grows none
 * (decision colonize-blob-grows-the-network).
 */
public interface ColonizeHost extends StepHost {

    /**
     * Grows the landed-on network over the ground within the radius.
     *
     * @param radius the spread's reach in blocks
     * @return true when the blob landed on a network and grew it, false when it landed on none
     */
    boolean colonize(int radius);
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host where a landing blob can grow a shroom network (capability
 * {@link HostCapability#COLONIZE}): the network of the block it landed on
 * spreads, or a new mycelium network starts there
 * (decision colonize-blob-grows-the-network).
 */
public interface ColonizeHost extends StepHost {

    /**
     * Grows the landed-on network over the ground within the radius.
     *
     * @param radius the spread's reach in blocks
     */
    void colonize(int radius);
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host whose landing can start aging the block it landed on
 * (capability {@link HostCapability#AGING}).
 * old-blob-ages-valuables-slowly
 */
public interface AgingHost extends StepHost {

    /**
     * Starts aging the landed-on block, where the aging table names it.
     */
    void startAging();
}

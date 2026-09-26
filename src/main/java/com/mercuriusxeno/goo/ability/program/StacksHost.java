package com.mercuriusxeno.goo.ability.program;

/**
 * A host holding stacked blobs it can count and spend (capability
 * {@link HostCapability#STACKS}).
 */
public interface StacksHost extends StepHost {

    /**
     * Returns the blobs stacked on the host, live.
     *
     * @return the stack count
     */
    int stackCount();

    /**
     * Spends one stacked blob.
     */
    void decrementStack();
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host holding stacked goo it can count and spend (capability
 * {@link HostCapability#STACKS}).
 */
public interface StacksHost extends StepHost {

    /**
     * Returns the goo stacked on the host, live.
     *
     * @return the stack count
     */
    int stackCount();

    /**
     * Spends one stacked goo.
     */
    void decrementStack();
}

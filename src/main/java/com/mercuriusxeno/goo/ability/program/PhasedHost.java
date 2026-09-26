package com.mercuriusxeno.goo.ability.program;

/**
 * A host keeping a phased step's cursor across ticks (capability
 * {@link HostCapability#PHASED}).
 */
public interface PhasedHost extends StepHost {

    /**
     * Returns the phase cursor the host keeps for a running phased step.
     *
     * @return the live state, mutated in place by the step
     */
    PhasedState phased();
}

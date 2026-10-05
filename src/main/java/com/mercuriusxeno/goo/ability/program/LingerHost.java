package com.mercuriusxeno.goo.ability.program;

import java.util.List;

/**
 * A host where a landing ability can stand its own block (capability
 * {@link HostCapability#LINGER}): the block takes the steps and runs them on
 * its own block host from that tick (decision
 * lingering-abilities-place-their-own-thing).
 */
public interface LingerHost extends StepHost {

    /**
     * Places the ability's own block at the landing and hands it the steps it runs.
     *
     * @param steps the steps the block runs on its own host
     */
    void linger(List<Step> steps);
}

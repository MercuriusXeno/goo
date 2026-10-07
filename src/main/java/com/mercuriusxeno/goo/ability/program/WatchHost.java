package com.mercuriusxeno.goo.ability.program;

import java.util.OptionalDouble;
import java.util.Set;

/**
 * A host that watches the entities around it and pulses to its viewers as
 * the nearest draws close (capability {@link HostCapability#WATCH}).
 * decision lurker-blob-brightens-then-detonates
 */
public interface WatchHost extends StepHost {

    /**
     * Finds the nearest entity every filter keeps within a sphere around the anchor.
     *
     * @param radius  the sphere radius in blocks
     * @param filters the filters an entity must pass
     * @return the nearest kept entity's distance, empty when none stands in the sphere
     */
    OptionalDouble nearestEntityDistance(double radius, Set<EntityFilter> filters);

    /**
     * Tells the host's viewers how near the watched entity stands, so they
     * draw it pulsing.
     *
     * @param distance the nearest entity's distance in blocks
     * @param radius   the radius the host watches, in blocks
     */
    void pulse(double distance, double radius);
}

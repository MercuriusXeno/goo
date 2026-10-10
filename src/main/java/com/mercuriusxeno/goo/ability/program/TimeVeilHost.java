package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can slow time in a sphere around its anchor: the marker a
 * chronosphere stands.
 * chronosphere-hastes-players-slows-mobs
 */
public interface TimeVeilHost extends StepHost {

    /**
     * Slows every non-player living entity and every projectile within a
     * radius of the anchor this tick.
     *
     * @param radius the sphere's radius in blocks
     * @param slow   the share of its motion each keeps a tick, 0 to 1
     */
    void slowWithin(double radius, double slow);
}

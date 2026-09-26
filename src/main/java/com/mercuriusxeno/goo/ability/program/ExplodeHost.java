package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can detonate at its anchor (capability {@link HostCapability#EXPLODE}).
 */
public interface ExplodeHost extends StepHost {

    /**
     * Detonates at the anchor's center.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    void explode(float power, ExplosionMode mode);
}

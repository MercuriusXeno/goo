package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can explode at its anchor (capability {@link HostCapability#EXPLODE}).
 */
public interface ExplodeHost extends StepHost {

    /**
     * Detonates at the anchor's center.
     *
     * @param power   the explosion power
     * @param reach   the radius the explosion cuts at most
     * @param gathers true to gather the mined drops into one compression sphere at the center
     * @param mode    how blocks are treated
     */
    void explode(float power, double reach, boolean gathers, ExplosionMode mode);
}

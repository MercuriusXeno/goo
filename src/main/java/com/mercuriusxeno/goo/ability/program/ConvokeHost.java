package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can sound a convoke's sculk call: draw the ring at its radius
 * and set every mob the ring crosses walking to the host
 * (decision convoke-is-a-sculk-call-that-draws-every-mob-the-ring-touches).
 */
public interface ConvokeHost extends StepHost {

    /**
     * Draws the ring and calls every mob between its two radii.
     *
     * @param inner the ring's radius at the start of this tick
     * @param outer the ring's radius at the end of this tick
     */
    void callRing(double inner, double outer);
}

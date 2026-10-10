package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can open a Dragon Gate over the surface its blob struck.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public interface GateHost extends StepHost {

    /**
     * Opens a Dragon Gate pair over the struck surface and the End's platform.
     *
     * @param lifetime the ticks the pair stands
     * @return true once the pair stands open
     */
    boolean openDragonGate(int lifetime);
}

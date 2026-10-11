package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can open an End gate over the surface its blob struck.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public interface GateHost extends StepHost {

    /**
     * Opens an End gate pair over the struck surface and the End's platform.
     *
     * @param lifetime the ticks the pair stands
     * @return true once the pair stands open
     */
    boolean openEndGate(int lifetime);
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host whose anchor can bank ticks: the timekeeper prism's marker.
 * timekeeper-prism-banks-ticks-forward-only
 */
public interface TickBankHost extends StepHost {

    /**
     * Banks one tick of standing on the anchor.
     *
     * @param perTick  the charge standing banks a tick
     * @param spending the most charge one held tick of Tick spends
     */
    void bankTicks(int perTick, int spending);
}

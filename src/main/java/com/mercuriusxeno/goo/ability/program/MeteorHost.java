package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can call a meteor down toward the cell its blob landed in.
 * decision meteo-crashes-through-whatever-is-above
 */
public interface MeteorHost extends StepHost {

    /**
     * Calls a meteor down toward the landing, crashing into whatever stands
     * above it.
     *
     * @param power     the explosion's power
     * @param fallTicks the ticks the meteor falls for
     */
    void callMeteor(float power, int fallTicks);
}

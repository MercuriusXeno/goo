package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can call a meteor down on the cell its blob landed in.
 * decision meteo-needs-a-clear-sky
 */
public interface MeteorHost extends StepHost {

    /**
     * Calls a meteor down on the landing when the sky above it is clear,
     * fizzling otherwise.
     *
     * @param power     the explosion's power
     * @param fallTicks the ticks the meteor falls for
     * @return true once a meteor falls
     */
    boolean callMeteor(float power, int fallTicks);
}

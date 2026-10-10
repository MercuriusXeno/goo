package com.mercuriusxeno.goo.ability.program;

/**
 * A host whose landing can tick the redstone device it landed on
 * (capability {@link HostCapability#POWER_PULSE}).
 * zap-ticks-the-device-and-stuns
 */
public interface PowerPulseHost extends StepHost {

    /**
     * Ticks the landed-on block once, as one pulse of power would.
     */
    void powerPulse();
}

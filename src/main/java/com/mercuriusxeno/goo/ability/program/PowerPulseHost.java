package com.mercuriusxeno.goo.ability.program;

/**
 * A host whose landing can tick the redstone device it landed on, or
 * disperse into the Signal wave where it landed on anything else
 * (capability {@link HostCapability#POWER_PULSE}).
 * zap-ticks-the-device-and-stuns
 * zap-disperses-into-signal
 */
public interface PowerPulseHost extends StepHost {

    /**
     * Ticks the landed-on block once, as one pulse of power would, where it
     * is a redstone device; any other block takes nothing.
     */
    void powerPulse();

    /**
     * Toggles each hand device behind the landed-on block once, where that
     * block is no redstone device; a device landed on sends no wave.
     *
     * @param range       the blocks the wave reaches past the landing
     * @param coneDegrees the wave's cone, apex to rim, in degrees
     */
    void signalWave(double range, double coneDegrees);
}

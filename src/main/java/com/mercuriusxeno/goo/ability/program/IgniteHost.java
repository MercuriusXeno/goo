package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can light a fire in its cell and set off the explosive
 * ability block it struck (capability {@link HostCapability#IGNITE}).
 * ignite-fire-and-explosive-combo
 */
public interface IgniteHost extends StepHost {

    /**
     * Lights a fire in the landing cell where fire can stand there.
     */
    void lightFire();

    /**
     * Sets off the explosive ability block the landing struck, or the one
     * standing in its cell: the block's burnout plays, the block goes, and a
     * fiery Goo explosion takes its place.
     *
     * @param power the explosion's power
     * @return true when an explosive block was set off
     */
    boolean detonateExplosive(float power);
}

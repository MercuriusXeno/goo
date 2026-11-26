package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can light a fire in its cell (capability
 * {@link HostCapability#IGNITE}); Nitro the fire heats explodes by its own rule.
 * ignite-fire-and-explosive-combo
 * ignite-detonate-step-leaves-with-the-ability-block
 */
public interface IgniteHost extends StepHost {

    /**
     * Lights a fire in the landing cell where fire can stand there.
     */
    void lightFire();
}

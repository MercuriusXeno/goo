package com.mercuriusxeno.goo.ability.program;

/**
 * A host that carries redstone signals between relays linked through air
 * (capability {@link HostCapability#RELAY}).
 * relay-prism-carries-the-signal-through-air
 */
public interface RelayHost extends StepHost {

    /**
     * Gives, for this tick, the strongest signal reaching any relay the host
     * links to through air, and none when no signal reaches one.
     */
    void carrySignal();
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can toggle the redstone device standing where it acts, as a
 * hand would (capability {@link HostCapability#TOGGLE_DEVICE}).
 * pulser-drip-toggles-the-block-below
 */
public interface DeviceToggleHost extends StepHost {

    /**
     * Toggles the lever, button, door, trapdoor or fence gate the host acts
     * on, once; where none stands, nothing happens.
     */
    void toggleDevice();
}

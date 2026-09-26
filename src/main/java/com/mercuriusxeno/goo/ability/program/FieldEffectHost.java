package com.mercuriusxeno.goo.ability.program;

/**
 * A host keeping a field effect's state across ticks (capability
 * {@link HostCapability#FIELD_EFFECT}).
 */
public interface FieldEffectHost extends StepHost {

    /**
     * Returns the field-effect state the host keeps for a running field effect.
     *
     * @return the live state, mutated in place by the step
     */
    FieldEffectState fieldEffect();
}

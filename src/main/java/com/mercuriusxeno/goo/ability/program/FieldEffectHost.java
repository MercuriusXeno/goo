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

    /**
     * Rolls a fraction the field effect weighs against its spend chance,
     * drawn from the host's world so a test can fix it (decision
     * metal-spends-charge-by-chance).
     *
     * @return a fraction in [0, 1)
     */
    double rollFraction();
}

package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can lengthen the timed effects standing on it, as a drunk
 * pulse brew does (capability {@link HostCapability#EXTEND_EFFECTS}).
 * extender-multiplies-the-next-self-duration
 */
public interface EffectExtendHost extends StepHost {

    /**
     * Lengthens every timed effect standing on the host by the brew's
     * duration; a glove invocation, which carries no brew duration, lengthens nothing.
     */
    void extendTimedEffects();
}

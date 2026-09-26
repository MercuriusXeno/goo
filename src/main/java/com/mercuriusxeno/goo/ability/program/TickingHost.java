package com.mercuriusxeno.goo.ability.program;

/**
 * A host a driver ticks past the program's first tick (capability
 * {@link HostCapability#TICKING}). It adds no verb: implementing it is the
 * promise of later ticks that a waiting step needs.
 */
public interface TickingHost extends StepHost {
}

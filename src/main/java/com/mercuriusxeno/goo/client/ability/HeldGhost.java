package com.mercuriusxeno.goo.client.ability;

/**
 * What a held ghost draws at the aim point while right click is held: its
 * landing dome's radius, and the radius the rings rippling out across the
 * aimed face span. Each goo type names its own, so a landing whose reach
 * differs from its dome rings to that reach.
 * held-visual-ghosts-the-landing-in-two-passes
 *
 * @param domeRadius the dome's radius in blocks
 * @param ringRadius the radius the rings span, in blocks
 */
public record HeldGhost(float domeRadius, float ringRadius) {

    /**
     * A ghost whose rings travel outward to the dome's own radius.
     *
     * @param domeRadius the dome's radius in blocks
     * @return the ghost
     */
    public static HeldGhost outwardTo(float domeRadius) {
        return new HeldGhost(domeRadius, domeRadius);
    }
}

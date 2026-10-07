package com.mercuriusxeno.goo.client.ability;

/**
 * What a held ghost draws at the aim point while right click is held: its
 * landing dome's radius, and the rings rippling across the aimed face, their
 * direction and the radius they span. Each goo type names its own, so a
 * landing whose reach differs from its dome rings to that reach.
 * held-visual-ghosts-the-landing-in-two-passes
 *
 * @param domeRadius the dome's radius in blocks
 * @param rings      which way the rings travel
 * @param ringRadius the radius the rings span, in blocks
 */
public record HeldGhost(float domeRadius, RingDirection rings, float ringRadius) {

    /**
     * Which way a held ghost's rings travel across the aimed face.
     */
    public enum RingDirection {
        /** Born at the aim point, growing out to the ring radius. */
        OUTWARD
    }

    /**
     * A ghost whose rings travel outward to the dome's own radius.
     *
     * @param domeRadius the dome's radius in blocks
     * @return the ghost
     */
    public static HeldGhost outwardTo(float domeRadius) {
        return new HeldGhost(domeRadius, RingDirection.OUTWARD, domeRadius);
    }
}

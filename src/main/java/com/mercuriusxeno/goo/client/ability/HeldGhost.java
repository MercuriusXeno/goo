package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.overlay.RippleRings;

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
        OUTWARD,
        /**
         * Born at the ring radius, closing on the aim point.
         * black-hole-rings-pulse-inward-to-the-pull-radius
         */
        INWARD;

        /**
         * A ring's radius at a phase, travelling this way across the ring radius.
         *
         * @param phase      the ring's phase in [0, 1)
         * @param ringRadius the radius the rings span, in blocks
         * @return the radius in blocks
         */
        public double radius(double phase, double ringRadius) {
            return this == OUTWARD ? RippleRings.ringRadius(phase, ringRadius)
                    : RippleRings.inwardRingRadius(phase, ringRadius);
        }

        /**
         * A ring's opacity at a phase, travelling this way.
         *
         * @param phase the ring's phase in [0, 1)
         * @return the opacity in [0, 1]
         */
        public double opacity(double phase) {
            return this == OUTWARD ? RippleRings.ringOpacity(phase) : RippleRings.inwardRingOpacity(phase);
        }
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

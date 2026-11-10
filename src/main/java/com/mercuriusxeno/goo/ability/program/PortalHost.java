package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can place a Quantum portal on its struck face for its caster.
 * portals-link-by-looking-through-portal-vision
 */
public interface PortalHost extends StepHost {

    /**
     * Places a portal on the struck face for the caster; a landing with no
     * caster places none.
     *
     * @return true when a portal was placed
     */
    boolean placePortal();
}

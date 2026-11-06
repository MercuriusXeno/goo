package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can carry its caster through a wormhole from the struck face
 * to the first opening ahead.
 * hole-closes-the-space-to-an-opening-ahead
 */
public interface WormholeHost extends StepHost {

    /**
     * Carries the caster through to the first opening ahead, or shows the
     * hole collapse at the face where none lies within reach.
     *
     * @param reach how many cells past the struck block the scan runs
     * @return true when the caster was carried through
     */
    boolean carryThrough(int reach);
}

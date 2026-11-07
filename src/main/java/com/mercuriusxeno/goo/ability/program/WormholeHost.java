package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can open a wormhole from its struck face to the first
 * opening ahead.
 * hole-closes-the-space-to-an-opening-ahead
 */
public interface WormholeHost extends StepHost {

    /**
     * Opens the hole's two mouths, or shows the hole collapse at the face
     * where no opening lies within reach.
     *
     * @param reach    how many cells past the struck block the scan runs
     * @param lifetime the ticks the mouths stand
     * @return true when the hole opened
     */
    boolean openHole(int reach, int lifetime);
}

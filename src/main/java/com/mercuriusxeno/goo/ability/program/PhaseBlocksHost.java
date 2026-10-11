package com.mercuriusxeno.goo.ability.program;

/**
 * A landing that can put the blocks behind its struck face out of phase.
 * portable-hole-phases-blocks-for-a-while
 */
public interface PhaseBlocksHost extends StepHost {

    /**
     * Phases a square tunnel from the struck block inward to a depth.
     *
     * @param depth    how many cells the hole runs
     * @param radius   how many cells the square reaches out from the line
     * @param lifetime the ticks before each cell steps back into phase
     * @return the cells put out of phase
     */
    int phaseBlocks(int depth, int radius, int lifetime);
}

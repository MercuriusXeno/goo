package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can convoke: pull a mob from somewhere in its chunk to the
 * spot it names, with the blink effect at both ends.
 * Decisions convoke-blob-throbs-until-a-mob-arrives and convoke-drip-rolls-a-small-chance.
 */
public interface ConvokeHost extends StepHost {

    /**
     * The game time now, which a convoke's pulse period reads.
     *
     * @return the level's game time
     */
    long gameTime();

    /**
     * Pulls one mob from the host's chunk to its convoke spot, or pulses the
     * blink effect where none comes.
     *
     * @return true once a mob stands at the spot
     */
    boolean convokeFromChunk();
}

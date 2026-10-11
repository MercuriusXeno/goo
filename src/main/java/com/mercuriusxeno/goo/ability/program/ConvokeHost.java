package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

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
     * @param goo the goo type whose color the afterimages wear
     * @return true once a mob stands at the spot
     */
    boolean convokeFromChunk(ResourceKey<GooTypeDefinition> goo);
}

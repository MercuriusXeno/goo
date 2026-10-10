package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;

/**
 * A host that hears the redstone signals reaching its block and keeps the
 * beat they set (capability {@link HostCapability#BEAT}).
 * metronome-prism-pulses-at-the-learned-rate
 */
public interface BeatHost extends StepHost {

    /**
     * The beat the host's block has heard.
     *
     * @return the beat, {@link RedstoneBeat#SILENT} for a block that hears none
     */
    RedstoneBeat beat();

    /**
     * The game time now.
     *
     * @return the level's game time
     */
    long gameTime();
}

package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;

/**
 * Which way a living thing's phase shifts on its tick: out of phase as it
 * stands in Phase goo while in phase, back into phase once the phase it laid
 * has faded off the goo, and neither otherwise. Each shift sounds.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 */
enum PhaseShift {

    /** The thing slips out of phase, standing in the goo. */
    OUT_OF_PHASE,
    /** The thing's phase has faded off the goo, so it steps back into phase. */
    INTO_PHASE,
    /** The thing's phase holds as it stood. */
    NONE;

    /**
     * The shift a thing's tick makes.
     *
     * @param inGoo    whether it stands in Phase goo this tick
     * @param standing the phase it holds before this tick lays any
     * @param now      the game time
     * @return the shift
     */
    static PhaseShift of(boolean inGoo, OutOfPhase standing, long now) {
        boolean phased = standing.standsAt(now);
        if (inGoo) {
            return phased ? NONE : OUT_OF_PHASE;
        }
        return standing.laid() && !phased ? INTO_PHASE : NONE;
    }
}

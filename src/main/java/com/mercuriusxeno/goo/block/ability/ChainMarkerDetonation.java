package com.mercuriusxeno.goo.block.ability;

/**
 * What firing a chain marker touches in the world, named so the order it
 * runs in holds under test: the burnout reaches the tracking players first,
 * before any program can remove the marker (decision
 * elemental-explosion-per-type).
 */
interface ChainMarkerDetonation {

    /** Sends the burnout to the players tracking the marker's chunk. */
    void announceBurnout();

    /**
     * Loads the marker's ability program.
     *
     * @return true when the marker holds a program to run
     */
    boolean loadProgram();

    /**
     * Runs the program's first tick.
     *
     * @return true when the program keeps running past it
     */
    boolean runFirstTick();

    /** Removes the marker block, when the block at its position is still a marker. */
    void removeMarker();

    /** Marks the marker changed and syncs its running program to clients. */
    void syncRunningProgram();

    /**
     * Fires a marker: announces the burnout, then runs its program's first
     * tick, removing the marker when no program runs past it.
     *
     * @param detonation the marker's world actions
     */
    static void fire(ChainMarkerDetonation detonation) {
        detonation.announceBurnout();
        if (!detonation.loadProgram()) {
            detonation.removeMarker();
            return;
        }
        if (!detonation.runFirstTick()) {
            detonation.removeMarker();
            return;
        }
        detonation.syncRunningProgram();
    }
}

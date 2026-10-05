package com.mercuriusxeno.goo.block.ability;

/**
 * What a chain marker touches in the world the tick its blob splats, named
 * so the order it runs in holds under test: the burnout reaches the
 * tracking players first, before any program can remove the marker, and
 * the program's first tick runs in the landing tick with no fuse between
 * (decisions elemental-explosion-per-type, splat-runs-the-program-no-fuse).
 */
interface ChainMarkerSplat {

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
     * Resolves a marker as its blob splats: announces the burnout, then runs
     * its program's first tick, removing the marker when no program runs
     * past it.
     *
     * @param splat the marker's world actions
     */
    static void resolve(ChainMarkerSplat splat) {
        splat.announceBurnout();
        if (!splat.loadProgram()) {
            splat.removeMarker();
            return;
        }
        if (!splat.runFirstTick()) {
            splat.removeMarker();
            return;
        }
        splat.syncRunningProgram();
    }
}

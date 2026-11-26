package com.mercuriusxeno.goo.ability.world;

/**
 * What a blob's landing touches in the world the tick it splats, named so
 * the order holds under test: the burnout reaches the tracking players
 * first, then the ability's program runs its first tick on the landing,
 * with no fuse between (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse). A blob that turns into its own block,
 * Prism's, fires none: its morph into the block is its landing (decision
 * prism-is-one-pointed-quartz-column).
 */
interface AbilitySplat {

    /** Sends the burnout to the players tracking the landing's chunk. */
    void announceBurnout();

    /** Runs the ability's program on the landing host. */
    void runProgram();

    /**
     * Whether the blob turns into the block its program places, so the morph
     * plays in place of a burnout.
     *
     * @return true for a program whose blob becomes its block
     */
    default boolean turnsIntoItsBlock() {
        return false;
    }

    /**
     * Resolves a landing: announces the burnout unless the blob turns into
     * its block, then runs the program.
     *
     * @param splat the landing's world actions
     */
    static void resolve(AbilitySplat splat) {
        if (!splat.turnsIntoItsBlock()) {
            splat.announceBurnout();
        }
        splat.runProgram();
    }
}

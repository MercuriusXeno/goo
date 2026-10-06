package com.mercuriusxeno.goo.ability.world;

/**
 * What a blob's landing touches in the world the tick it splats, named so
 * the order holds under test: the burnout reaches the tracking players
 * first, then the ability's program runs its first tick on the landing,
 * with no fuse between (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse). A program that lingers fires no burnout
 * at the splat: its standing block plays it when it explodes, so the
 * explosion's visual lands with the explosion.
 */
interface AbilitySplat {

    /** Sends the burnout to the players tracking the landing's chunk. */
    void announceBurnout();

    /** Runs the ability's program on the landing host. */
    void runProgram();

    /**
     * Whether the program stands its own block to run on after the splat.
     *
     * @return true for a program that lingers
     */
    boolean lingers();

    /**
     * Resolves a landing: announces the burnout unless the program lingers,
     * then runs the program.
     *
     * @param splat the landing's world actions
     */
    static void resolve(AbilitySplat splat) {
        if (!splat.lingers()) {
            splat.announceBurnout();
        }
        splat.runProgram();
    }
}

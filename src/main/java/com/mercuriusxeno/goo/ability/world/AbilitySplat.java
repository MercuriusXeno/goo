package com.mercuriusxeno.goo.ability.world;

/**
 * What a blob's landing touches in the world the tick it splats, named so
 * the order holds under test: the burnout reaches the tracking players
 * first, then the ability's program runs its first tick on the landing,
 * with no fuse between (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse). A program whose standing block explodes
 * later fires no burnout at the splat: the block plays it as it explodes, so
 * the explosion's visual lands with the explosion. A program that lingers
 * without exploding, Razor's cloud, plays it at the splat.
 */
interface AbilitySplat {

    /** Sends the burnout to the players tracking the landing's chunk. */
    void announceBurnout();

    /** Runs the ability's program on the landing host. */
    void runProgram();

    /**
     * Whether the program stands its own block that explodes after the splat.
     *
     * @return true for a program whose standing block explodes later
     */
    boolean explodesLater();

    /**
     * Resolves a landing: announces the burnout unless the program explodes
     * later, then runs the program.
     *
     * @param splat the landing's world actions
     */
    static void resolve(AbilitySplat splat) {
        if (!splat.explodesLater()) {
            splat.announceBurnout();
        }
        splat.runProgram();
    }
}

package com.mercuriusxeno.goo.ability.world;

/**
 * What a blob's landing touches in the world the tick it splats, named so
 * the order holds under test: the burnout reaches the tracking players
 * first, then the ability's program runs its first tick on the landing,
 * with no fuse between (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse). A program whose standing block explodes
 * later fires no burnout at the splat: the block plays it as it explodes, so
 * the explosion's visual lands with the explosion. A program that lingers
 * without exploding, Razor's cloud, plays it at the splat. A blob that
 * turns into its own block, Prism's, fires none: its morph into the block
 * is its landing (decision prism-is-one-pointed-quartz-column).
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
     * Whether the blob turns into the block its program places, so the morph
     * plays in place of a burnout.
     *
     * @return true for a program whose blob becomes its block
     */
    default boolean turnsIntoItsBlock() {
        return false;
    }

    /**
     * Resolves a landing: announces the burnout unless the program explodes
     * later or the blob turns into its block, then runs the program.
     *
     * @param splat the landing's world actions
     */
    static void resolve(AbilitySplat splat) {
        if (!splat.explodesLater() && !splat.turnsIntoItsBlock()) {
            splat.announceBurnout();
        }
        splat.runProgram();
    }
}

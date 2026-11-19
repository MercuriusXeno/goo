package com.mercuriusxeno.goo.ability.nether;

/**
 * Whether Invert holds where a player stands. It holds while a block that
 * stops a fall stands over the player's body, and ends the moment the player
 * is exposed to the sky, so nobody falls up out of the world.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
public final class InvertSky {

    /** What the sky over an inverted player asks of Invert. */
    public enum Verdict {
        /** A roof stands over the body: Invert holds. */
        HOLD,
        /** The body is open to the sky: Invert ends. */
        END
    }

    private InvertSky() {
    }

    /**
     * Answers whether Invert holds over a body.
     *
     * @param bodyTop    the top of the player's bounding box, the side gravity pulls toward under Invert
     * @param roofHeight the lowest height over the player's column with no block that stops a fall at or above it,
     *                   the column's motion blocking heightmap
     * @return HOLD under a roof, END open to the sky
     */
    public static Verdict verdict(double bodyTop, int roofHeight) {
        return Math.floor(bodyTop) < roofHeight ? Verdict.HOLD : Verdict.END;
    }
}

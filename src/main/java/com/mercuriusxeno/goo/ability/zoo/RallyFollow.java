package com.mercuriusxeno.goo.ability.zoo;

/**
 * What a rallied mob does each tick: it fights a foe in reach, and with none
 * it walks to its rallier from beyond the near distance and holds within it.
 * rallied-mobs-follow-the-player-who-rallied-them
 */
public final class RallyFollow {

    /** How close to its rallier a rallied mob comes before it holds, in blocks. */
    static final double NEAR_DISTANCE = 4.0;

    /** A rallied mob's move this tick. */
    enum Move {
        /** Chase and strike the foe in reach. */
        FIGHT,
        /** Walk toward the rallier. */
        FOLLOW,
        /** Stand where it is, near the rallier. */
        HOLD
    }

    private RallyFollow() {
    }

    /**
     * Chooses a rallied mob's move.
     *
     * @param foeInReach           whether a foe of the rallier stands in the mob's reach
     * @param distanceSqrToRallier the squared distance from the mob to its rallier
     * @return the move
     */
    static Move choose(boolean foeInReach, double distanceSqrToRallier) {
        if (foeInReach) {
            return Move.FIGHT;
        }
        return distanceSqrToRallier > NEAR_DISTANCE * NEAR_DISTANCE ? Move.FOLLOW : Move.HOLD;
    }
}

package com.mercuriusxeno.goo.ability.zoo;

/**
 * What Kin tells a mob each tick: a passive mob in reach walks to the
 * player from beyond the short distance and holds within it. A hostile mob,
 * a mob beyond the radius and a rallied mob are left alone, a rallied mob's
 * own AI driving it.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
public final class KinFollow {

    /** A mob's move from Kin this tick. */
    enum Move {
        /** Walk toward the player holding Kin. */
        LEAD,
        /** Stand where it is, a short distance from the player. */
        HOLD,
        /** Receive no move from Kin. */
        LEAVE_ALONE
    }

    private KinFollow() {
    }

    /**
     * Chooses a mob's move from Kin.
     *
     * @param peaceful           whether the mob is a passive mob
     * @param rallied            whether the mob holds a rally
     * @param distanceSqrToHolder the squared distance from the mob to the player holding Kin
     * @param kin             the Kin the player holds
     * @return the move
     */
    static Move choose(boolean peaceful, boolean rallied, double distanceSqrToHolder, Kin kin) {
        if (!peaceful || rallied || distanceSqrToHolder > kin.radius() * kin.radius()) {
            return Move.LEAVE_ALONE;
        }
        return distanceSqrToHolder > kin.distance() * kin.distance() ? Move.LEAD : Move.HOLD;
    }
}

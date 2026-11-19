package com.mercuriusxeno.goo.ability.yarn;

/**
 * What Herd tells a mob each tick: a passive mob in reach walks to the
 * player from beyond the short distance and holds within it. A hostile mob,
 * a mob beyond the radius and a led mob are left alone, a led mob's
 * own AI driving it.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
public final class HerdFollow {

    /** A mob's move from Herd this tick. */
    enum Move {
        /** Walk toward the player holding Herd. */
        APPROACH,
        /** Stand where it is, a short distance from the player. */
        HOLD,
        /** Receive no move from Herd. */
        LEAVE_ALONE
    }

    private HerdFollow() {
    }

    /**
     * Chooses a mob's move from Herd.
     *
     * @param peaceful           whether the mob is a passive mob
     * @param led            whether the mob holds a lead
     * @param distanceSqrToHolder the squared distance from the mob to the player holding Herd
     * @param herd             the Herd the player holds
     * @return the move
     */
    static Move choose(boolean peaceful, boolean led, double distanceSqrToHolder, Herd herd) {
        if (!peaceful || led || distanceSqrToHolder > herd.radius() * herd.radius()) {
            return Move.LEAVE_ALONE;
        }
        return distanceSqrToHolder > herd.distance() * herd.distance() ? Move.APPROACH : Move.HOLD;
    }
}

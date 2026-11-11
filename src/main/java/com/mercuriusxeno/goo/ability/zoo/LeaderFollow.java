package com.mercuriusxeno.goo.ability.zoo;

/**
 * What Leader tells a mob each tick: a passive mob in reach walks to the
 * player from beyond the short distance and holds within it. A hostile mob,
 * a mob beyond the radius and a rallied mob are left alone, a rallied mob's
 * own AI driving it.
 * leader-leads-passive-mobs-at-a-short-distance
 * rallied-mob-ai-trumps-leader
 */
public final class LeaderFollow {

    /** A mob's move from Leader this tick. */
    enum Move {
        /** Walk toward the player holding Leader. */
        LEAD,
        /** Stand where it is, a short distance from the player. */
        HOLD,
        /** Receive no move from Leader. */
        LEAVE_ALONE
    }

    private LeaderFollow() {
    }

    /**
     * Chooses a mob's move from Leader.
     *
     * @param peaceful           whether the mob is a passive mob
     * @param rallied            whether the mob holds a rally
     * @param distanceSqrToLeader the squared distance from the mob to the player holding Leader
     * @param leader             the Leader the player holds
     * @return the move
     */
    static Move choose(boolean peaceful, boolean rallied, double distanceSqrToLeader, Leader leader) {
        if (!peaceful || rallied || distanceSqrToLeader > leader.radius() * leader.radius()) {
            return Move.LEAVE_ALONE;
        }
        return distanceSqrToLeader > leader.distance() * leader.distance() ? Move.LEAD : Move.HOLD;
    }
}

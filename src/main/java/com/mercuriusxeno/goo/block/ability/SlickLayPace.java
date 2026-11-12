package com.mercuriusxeno.goo.block.ability;

/**
 * How fast Kinetic's slick lays under a channeler: a channeler standing on
 * slick and channeling slick lays it faster by their velocity over their
 * unadjusted movement speed, so the player's speed sets how fast it
 * resolves, and the slick lands in time to step on.
 * slick-resolves-faster-by-speed-over-unadjusted-speed
 */
public final class SlickLayPace {

    /** Vanilla's share of a movement key's input a walking entity keeps each tick. */
    private static final double INPUT_FRICTION = 0.98;
    /** Vanilla's share of a walking entity's velocity ordinary ground, friction 0.6, keeps each tick. */
    private static final double GROUND_DRAG = 0.6 * 0.91;
    /** The floor of the factor: a channeler slower than their walk lays at the JSON's ticks, never slower. */
    private static final double JSON_PACE = 1;

    private SlickLayPace() {
    }

    /**
     * The horizontal speed a movement speed attribute walks a player at on
     * ordinary ground once the walk settles: on friction 0.6 ground vanilla
     * adds the attribute times the kept input each tick and keeps
     * GROUND_DRAG of the velocity, so the walk settles where the two balance.
     *
     * @param movementSpeed the player's movement speed attribute
     * @return the unadjusted speed, in blocks a tick
     */
    public static double unadjustedSpeed(double movementSpeed) {
        return movementSpeed * INPUT_FRICTION / (1 - GROUND_DRAG);
    }

    /**
     * The factor a channeler's speed multiplies the slick's laying rate by:
     * their horizontal speed over their unadjusted speed while they stand on
     * slick, and never below one.
     *
     * @param travelSpeed     the channeler's horizontal speed, in blocks a tick
     * @param unadjustedSpeed the channeler's unadjusted movement speed, in blocks a tick
     * @param standsOnSlick   whether the channeler stands on slick
     * @return the factor
     */
    public static double factor(double travelSpeed, double unadjustedSpeed, boolean standsOnSlick) {
        if (!standsOnSlick || unadjustedSpeed <= 0) {
            return JSON_PACE;
        }
        return Math.max(JSON_PACE, travelSpeed / unadjustedSpeed);
    }
}

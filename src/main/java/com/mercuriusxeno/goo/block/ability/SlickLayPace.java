package com.mercuriusxeno.goo.block.ability;

import net.minecraft.util.RandomSource;

/**
 * How fast Slick throws its drips: its base drips a tick, times the
 * caster's horizontal speed over the speed their movement speed walks them
 * at on ordinary ground, never below the base, so the goo it lays per block
 * the caster covers stays the same however fast they run. A tick's share of
 * a drip lands as a drip on a draw seeded by the hold's tick, so a hold
 * throws the same drips each time.
 * slick-resolves-faster-by-speed-over-unadjusted-speed
 */
public final class SlickLayPace {

    /** Vanilla's share of a movement key's input a walking entity keeps each tick. */
    private static final double INPUT_FRICTION = 0.98;
    /** Vanilla's share of a walking entity's velocity ordinary ground, friction 0.6, keeps each tick. */
    private static final double GROUND_DRAG = 0.6 * 0.91;
    /** The floor of the factor: a caster slower than their walk throws the base drips, never fewer. */
    private static final double BASE_PACE = 1;
    /** Spreads one hold tick's draw from the next, apart from the scatter's draw. */
    private static final long PACE_SEED_STRIDE = 0xC2B2AE3D27D4EB4FL;

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
     * The factor a caster's speed multiplies Slick's drips a tick by: their
     * horizontal speed over their unadjusted speed, never below one.
     *
     * @param travelSpeed     the caster's horizontal speed, in blocks a tick
     * @param unadjustedSpeed the caster's unadjusted walking speed, in blocks a tick
     * @return the factor
     */
    public static double factor(double travelSpeed, double unadjustedSpeed) {
        return unadjustedSpeed <= 0 ? BASE_PACE : Math.max(BASE_PACE, travelSpeed / unadjustedSpeed);
    }

    /**
     * The drips a tick of a hold throws: the base times the factor, its
     * whole drips, and its share of one more on a draw seeded by the tick.
     *
     * @param baseDrips the drips a tick at rest
     * @param factor    the caster's speed factor
     * @param held      the hold's tick count, 1 on its first tick
     * @return the tick's drips
     */
    public static int dripsThisTick(int baseDrips, double factor, int held) {
        double drips = baseDrips * factor;
        int whole = (int) Math.floor(drips);
        return whole + (RandomSource.create(held * PACE_SEED_STRIDE).nextDouble() < drips - whole ? 1 : 0);
    }
}

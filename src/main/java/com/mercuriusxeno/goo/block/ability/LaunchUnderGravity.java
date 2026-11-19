package com.mercuriusxeno.goo.block.ability;

/**
 * A launch's climb under a share of the overworld's gravity, with vanilla's air drag on a living entity: how high a
 * launch peaks, how long its flight lasts, and the launch that peaks as high, over the dimension's share, as an
 * overworld launch does in the overworld. Drag holds a lander under a weak pull near a low top speed, so the same
 * launch climbs well short of the share's inverse; the height is what scales with gravity, and the launch follows.
 * decision goop-bounce-height-is-proportionate-to-gravity
 */
final class LaunchUnderGravity {

    /** A living entity's gravity in the overworld, in blocks a tick each tick. */
    static final double OVERWORLD_GRAVITY = 0.08;
    /** The share of its vertical speed a living entity keeps through the air each tick. */
    static final double AIR_DRAG = 0.98;
    /** The halvings a search for a launch takes, past a double's precision over any launch a coat sends. */
    private static final int SEARCH_HALVINGS = 64;
    /** How much each step of the search's opening widens its reach until the reach climbs the height. */
    private static final double SEARCH_WIDENING = 2;
    private static final double HALF = 0.5;
    /** The ticks a drop is read for, past the hundreds a lunar fall takes to near the top speed drag holds it to. */
    private static final int LONGEST_DROP_TICKS = 2000;

    private LaunchUnderGravity() {
    }

    /**
     * How far a launch climbs before it turns: the launch tick takes gravity and drag before the lander first
     * moves, as every tick after it does.
     *
     * @param launch       the upward speed, in blocks a tick
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the blocks it climbs
     */
    static double peakOf(double launch, double gravityScale) {
        double gravity = OVERWORLD_GRAVITY * gravityScale;
        double climbed = 0;
        for (double speed = (launch - gravity) * AIR_DRAG; speed > 0; speed = (speed - gravity) * AIR_DRAG) {
            climbed += speed;
        }
        return climbed;
    }

    /**
     * How many ticks a launch flies before it falls back to the height it left.
     *
     * @param launch       the upward speed, in blocks a tick
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the ticks it flies
     */
    static long flightTicksOf(double launch, double gravityScale) {
        double gravity = OVERWORLD_GRAVITY * gravityScale;
        double speed = (launch - gravity) * AIR_DRAG;
        double height = speed;
        long ticks = 1;
        while (height > 0) {
            speed = (speed - gravity) * AIR_DRAG;
            height += speed;
            ticks++;
        }
        return ticks;
    }

    /**
     * The launch a lander under a share of the overworld's gravity takes to climb what an overworld launch climbs
     * in the overworld, over that share.
     *
     * @param overworldLaunch the launch in the overworld, in blocks a tick
     * @param gravityScale    the share of the overworld's gravity the lander falls under
     * @return the launch that climbs that high under that share
     */
    static double launchIn(double overworldLaunch, double gravityScale) {
        return launchReaching(peakOf(overworldLaunch, 1) / gravityScale, gravityScale);
    }

    /**
     * The overworld launch that climbs a speed's climb under a share of the overworld's gravity, times that share:
     * the inverse of {@link #launchIn}.
     *
     * @param speed        the speed under that share, in blocks a tick
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the overworld launch it stands for
     */
    static double overworldLaunchOf(double speed, double gravityScale) {
        return launchReaching(peakOf(speed, gravityScale) * gravityScale, 1);
    }

    /**
     * The overworld fall a fall under a share of the overworld's gravity stands for: the speed a drop from rest
     * reaches in the overworld over the height, times that share, the fall had to drop to reach its speed. A fall
     * home from a launch's top is a drop from rest, so this reads the height it fell rather than the climb its
     * speed would launch, which drag under a weak pull leaves well short of the height.
     *
     * @param arrivingFall the speed it fell onto the coat at, in blocks a tick
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the speed the overworld drop arrives at
     */
    static double overworldFallOf(double arrivingFall, double gravityScale) {
        return speedAfterDropping(dropToReach(arrivingFall, gravityScale) * gravityScale, 1);
    }

    /**
     * The height a drop from rest falls before it reaches a speed, or before it stops gaining speed where drag
     * holds it under that speed.
     *
     * @param speed        the speed, in blocks a tick
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the blocks it falls
     */
    private static double dropToReach(double speed, double gravityScale) {
        double gravity = OVERWORLD_GRAVITY * gravityScale;
        double fallen = 0;
        double falling = gravity * AIR_DRAG;
        for (int tick = 0; falling < speed && tick < LONGEST_DROP_TICKS; tick++) {
            fallen += falling;
            falling = (falling + gravity) * AIR_DRAG;
        }
        return fallen;
    }

    /**
     * The speed a drop from rest reaches once it has fallen a height.
     *
     * @param height       the blocks it falls
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the speed, in blocks a tick
     */
    private static double speedAfterDropping(double height, double gravityScale) {
        double gravity = OVERWORLD_GRAVITY * gravityScale;
        double fallen = 0;
        double falling = 0;
        while (fallen < height) {
            falling = (falling + gravity) * AIR_DRAG;
            fallen += falling;
        }
        return falling;
    }

    /**
     * The least launch that climbs a height under a share of the overworld's gravity, found by halving, since a
     * launch's climb rises with it.
     *
     * @param height       the blocks to climb
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the launch, 0 for no height
     */
    static double launchReaching(double height, double gravityScale) {
        if (height <= 0) {
            return 0;
        }
        double low = 0;
        double high = 1;
        while (peakOf(high, gravityScale) < height) {
            low = high;
            high *= SEARCH_WIDENING;
        }
        for (int halving = 0; halving < SEARCH_HALVINGS; halving++) {
            double middle = low + (high - low) * HALF;
            if (peakOf(middle, gravityScale) < height) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }
}

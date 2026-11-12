package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * How Drain's streamed wisps fly, as Decay's gnats do ({@link GnatSteering}):
 * each tick the player's pull is added to the velocity the wisp already has,
 * with a swirl around the line to the player, a random dart and a little drag.
 * The pull grows the longer the wisp flies, so it leaves the drained mob
 * loose and volatile, wheeling and darting, and is drawn in faster and
 * faster until it pours into the player; the swirl dies out as it arrives so
 * it lands. drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
final class WispStream {

    /** The most ticks a streamed wisp flies before it is gone, landed or not. */
    static final int MOST_TICKS = 60;
    /** Within this many blocks of the player a wisp has landed. */
    static final double LANDS_WITHIN = 0.3;
    /** Within this many blocks of the player a wisp fades as it lands. */
    static final double FADES_WITHIN = 0.8;
    /**
     * The player's pull on a wisp as it shows, blocks per tick each tick, and
     * how it grows with the square of its ticks shown.
     */
    private static final double PULL = 0.07;
    private static final double PULL_GROWTH = 0.006;
    /** Within this many blocks of the player the pull eases off, so the wisp settles onto them. */
    private static final double ARRIVING_WITHIN = 0.7;
    /** The share of its velocity a wisp keeps each tick. */
    private static final double DRAG = 0.8;
    /**
     * How hard the swirl pushes across the line, blocks per tick, how fast it
     * wheels, radians a tick, and within how many blocks of the player it dies out.
     */
    private static final double SWIRL = 0.06;
    private static final double SWIRL_TURN = 0.45;
    private static final double SWIRL_FADES_WITHIN = 1.2;
    /** The widest a wisp's velocity darts each tick on each axis, half of it either way. */
    private static final double DART_SPAN = 0.08;
    private static final double HALF = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a vector has no direction. */
    private static final double NO_WAY = 1e-9;

    private WispStream() {
    }

    /**
     * A streamed wisp's velocity after one tick.
     *
     * @param velocity its velocity, blocks per tick
     * @param at       where it is
     * @param home     where the player draws it to this tick
     * @param shown    ticks since it showed
     * @param random   the random source its darts draw on
     * @param sway     its own phase, radians, so no two wisps wheel as one
     * @return the new velocity
     */
    static Vec3 step(Vec3 velocity, Vec3 at, Vec3 home, int shown, RandomSource random, double sway) {
        Vec3 toHome = home.subtract(at);
        double gap = toHome.length();
        if (gap * gap < NO_WAY) {
            return velocity.scale(DRAG);
        }
        Vec3 way = toHome.scale(1 / gap);
        Vec3 pull = way.scale(PULL * (1 + PULL_GROWTH * shown * shown) * Math.min(1, gap / ARRIVING_WITHIN));
        Vec3 side = way.cross(UP);
        side = side.lengthSqr() < NO_WAY ? EAST : side.normalize();
        Vec3 lift = side.cross(way);
        double wheel = sway + shown * SWIRL_TURN;
        Vec3 swirl = side.scale(Math.cos(wheel)).add(lift.scale(Math.sin(wheel)))
                .scale(SWIRL * Math.min(1, gap / SWIRL_FADES_WITHIN));
        Vec3 dart = new Vec3(random.nextDouble() - HALF, random.nextDouble() - HALF, random.nextDouble() - HALF)
                .scale(DART_SPAN);
        return velocity.add(pull).add(swirl).add(dart).scale(DRAG);
    }
}

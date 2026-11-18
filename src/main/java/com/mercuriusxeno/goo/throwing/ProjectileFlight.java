package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;

/**
 * A thrown projectile's flight, a tick at a time, in the order vanilla ticks
 * an arrow: it moves by its velocity, so its first tick covers the speed it
 * was launched at, then air drags the velocity and gravity pulls it down, so
 * each tick after drops it further. The speed and the gravity are its
 * inputs; of the living boxes and the blocks a tick's segment meets, the
 * nearest ends it. The server's flights and the drawn flights step through
 * this one flight.
 * decision knives-and-javelins-drop-like-an-arrow
 * decision knives-and-dart-function-as-projectiles
 */
public final class ProjectileFlight {

    /** An arrow's ballistics: vanilla's 0.05 gravity and 0.99 inertia in air. */
    public static final Ballistics ARROW = new Ballistics(0.05, 0.99F);

    /**
     * How the air bends a projectile's velocity each tick.
     *
     * @param gravity the pull taken off its upward velocity each tick, blocks per tick per tick
     * @param drag    the share of its velocity it keeps each tick
     */
    public record Ballistics(double gravity, double drag) {
    }

    private ProjectileFlight() {
    }

    /**
     * A projectile's launch velocity along a look at a speed.
     *
     * @param look  the look, any length
     * @param speed the speed, blocks per tick
     * @return the launch velocity
     */
    public static Vec3 launch(Vec3 look, double speed) {
        return look.normalize().scale(speed);
    }

    /**
     * One tick of a projectile's flight.
     *
     * @param position   where it stands
     * @param velocity   its velocity before the tick, blocks per tick
     * @param ballistics how the air bends its velocity
     * @param clip       the blocks its segment may meet
     * @param living     the living its segment may meet
     * @param airspace   where it may fly
     * @return where the tick leaves it
     */
    public static DripArc.Step step(Vec3 position, Vec3 velocity, Ballistics ballistics, CoatRay.BlockClip clip,
                                    DripArc.LivingNear living, DripArc.Airspace airspace) {
        Vec3 after = velocity.scale(ballistics.drag()).subtract(0, ballistics.gravity(), 0);
        return DripArc.moveAlong(position, position.add(velocity), after, clip, living, airspace);
    }
}

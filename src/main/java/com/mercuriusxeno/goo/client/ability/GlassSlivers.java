package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The slivers a shattered glass knife cracks into: a few splinters thrown
 * out from where it broke, tumbling as they drop, shrinking and fading
 * away over about a second, so the knife melts into glass rather than
 * vanishing.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class GlassSlivers {

    /** Splinters one knife cracks into. */
    static final int PER_KNIFE = 5;
    /** Ticks a splinter lasts from the crack to gone. */
    static final float LIFE_TICKS = 20f;
    /** Blocks per tick squared a splinter falls by. */
    static final double GRAVITY = 0.03;
    /** A splinter's length at the crack, in blocks. */
    static final float LENGTH = 0.16f;
    /** How fast a splinter is thrown out from the crack, in blocks a tick. */
    private static final double THROW = 0.07;
    /** How fast a splinter is thrown upward at most, in blocks a tick. */
    private static final double LIFT = 0.08;
    /** Radians a splinter tumbles each tick. */
    private static final double TUMBLE_PER_TICK = 0.6;
    private static final double HALF = 0.5;
    private static final double BOTH_WAYS = 2;

    private final List<Sliver> live = new ArrayList<>();

    /**
     * One splinter.
     *
     * @param from      where the knife cracked
     * @param velocity  its velocity as it is thrown out, in blocks a tick
     * @param spinAxis  the unit axis it tumbles about
     * @param bornAt    the game time the knife cracked
     */
    record Sliver(Vec3 from, Vec3 velocity, Vec3 spinAxis, double bornAt) {

        /**
         * Where the splinter is an age after the crack, falling as it flies.
         *
         * @param age ticks since the crack
         * @return its middle
         */
        Vec3 positionAt(double age) {
            return from.add(velocity.scale(age)).subtract(0, HALF * GRAVITY * age * age, 0);
        }

        /**
         * How much of the splinter is left an age after the crack: whole at
         * the crack, shrinking and fading to nothing over its life.
         *
         * @param age ticks since the crack
         * @return its share left, 0 to 1
         */
        float leftAt(double age) {
            return (float) Math.clamp(1 - age / LIFE_TICKS, 0, 1);
        }

        /**
         * The splinter's heading an age after the crack, tumbling about its axis.
         *
         * @param age ticks since the crack
         * @return its unit heading
         */
        Vec3 headingAt(double age) {
            Vec3 start = spinAxis.cross(new Vec3(0, 1, 0)).lengthSqr() > 0
                    ? spinAxis.cross(new Vec3(0, 1, 0)).normalize() : new Vec3(1, 0, 0);
            Vec3 side = spinAxis.cross(start);
            double angle = age * TUMBLE_PER_TICK;
            return start.scale(Math.cos(angle)).add(side.scale(Math.sin(angle))).normalize();
        }
    }

    /**
     * Cracks a knife into its splinters where it broke.
     *
     * @param at     where it broke
     * @param now    the game time it broke
     * @param random the scatter's source
     */
    void crack(Vec3 at, double now, RandomSource random) {
        for (int index = 0; index < PER_KNIFE; index++) {
            Vec3 velocity = new Vec3(scatter(random) * THROW, random.nextDouble() * LIFT, scatter(random) * THROW);
            Vec3 axis = new Vec3(scatter(random), scatter(random), scatter(random));
            live.add(new Sliver(at, velocity, axis.lengthSqr() > 0 ? axis.normalize() : new Vec3(0, 1, 0), now));
        }
    }

    /**
     * Draws every splinter and drops those gone.
     *
     * @param quads  the context the faces emit through
     * @param now    the game time with its partial tick
     * @param camera the camera's position
     */
    void draw(FlatQuadContext quads, double now, Vec3 camera) {
        live.removeIf(sliver -> now - sliver.bornAt() >= LIFE_TICKS);
        for (Sliver sliver : live) {
            double age = Math.max(0, now - sliver.bornAt());
            float left = sliver.leftAt(age);
            GlassKunai.emitSplinter(quads, sliver.positionAt(age).subtract(camera), sliver.headingAt(age),
                    LENGTH * left, left);
        }
    }

    /**
     * Whether any splinter is left.
     *
     * @return true while a splinter falls
     */
    boolean isEmpty() {
        return live.isEmpty();
    }

    /** Drops every splinter, as a disconnect does. */
    void clear() {
        live.clear();
    }

    private static double scatter(RandomSource random) {
        return (random.nextDouble() - HALF) * BOTH_WAYS;
    }
}

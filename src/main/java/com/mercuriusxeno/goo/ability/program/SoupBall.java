package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Where Unmake's soup ball rides and how big it is: it hovers in front of the
 * player along the look, growing with the goo it holds; where something stands
 * in its way it moves closer, and once it can come no closer it compresses
 * into what it touches.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SoupBall {

    /** How far in front of the eye the ball's middle rides when it has room, in blocks. */
    public static final double DISTANCE = 2.0;
    /** The closest the ball's middle comes to the eye, in blocks. */
    public static final double CLOSEST = 0.9;
    /** The ball's radius while it holds nothing, in blocks. */
    public static final double MIN_RADIUS = 0.15;
    /** The radius the ball grows toward, half a block, a block across. */
    public static final double MAX_RADIUS = 0.5;
    /** The goo, in mB, over which the ball grows most of the way to its full size. */
    static final double GROWTH_GOO = 12_000;
    /** The flattest the ball compresses, as a share of its radius along the look. */
    static final double FLATTEST = 0.35;

    private SoupBall() {
    }

    /**
     * Where the ball rides and how far it is compressed.
     *
     * @param center its middle
     * @param depth  its depth along the look as a share of its radius, 1 round
     */
    public record Placement(Vec3 center, double depth) {
    }

    /**
     * Where the ball rides along the look, how far it is compressed.
     *
     * @param distance its middle's distance from the eye
     * @param depth    its depth along the look as a share of its radius, 1 round
     */
    record Along(double distance, double depth) {
    }

    /**
     * @param goo the goo the ball holds, in mB
     * @return its radius, in blocks
     */
    public static double radius(long goo) {
        return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * (1 - Math.exp(-Math.max(0, goo) / GROWTH_GOO));
    }

    /**
     * Places the ball before an eye, moved in or compressed by whatever
     * blocks the look.
     *
     * @param level  the level
     * @param eye    the eye
     * @param look   the look's unit vector
     * @param radius the ball's radius
     * @return the placement
     */
    public static Placement place(BlockGetter level, Vec3 eye, Vec3 look, double radius) {
        double reach = DISTANCE + radius;
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        double free = hit.getType() == HitResult.Type.MISS ? reach : hit.getLocation().distanceTo(eye);
        Along along = along(free, radius);
        return new Placement(eye.add(look.scale(along.distance())), along.depth());
    }

    /**
     * Where the ball rides with a given room along the look: as far as it may
     * with its surface short of what stands there, no closer than
     * {@link #CLOSEST}; and there, whatever room is left past its middle is
     * its depth.
     *
     * @param free   the room along the look before something stands, in blocks
     * @param radius the ball's radius
     * @return where it rides and how deep it is
     */
    static Along along(double free, double radius) {
        double distance = Math.clamp(free - radius, CLOSEST, DISTANCE);
        double depth = Math.clamp((free - distance) / radius, FLATTEST, 1);
        return new Along(distance, depth);
    }
}

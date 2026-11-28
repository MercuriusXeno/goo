package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where an ability leaving a source off the eye goes: the point the look
 * intersects inside the ability's range, or the look's point at the end of
 * that range where the look meets nothing, so what starts from the lifted
 * blob still lands on what the crosshair points at rather than running
 * beside the look's line.
 * decision ability-goes-where-the-look-intersects
 */
public final class LookAim {

    /** Below this squared length a source stands on its target and keeps the look. */
    private static final double MIN_AIM_LENGTH_SQUARED = 1.0E-6;

    /**
     * The first point a line meets, the world's blocks or whatever else the
     * caller counts.
     */
    @FunctionalInterface
    public interface LookClip {

        /**
         * Clips the line.
         *
         * @param from the line's start
         * @param to   the line's end
         * @return the first point the line meets, or {@code to} where it meets nothing
         */
        Vec3 firstPoint(Vec3 from, Vec3 to);
    }

    private LookAim() {
    }

    /**
     * What a caster's look meets: the first block outline, fluids passed
     * through, or a living thing standing before it, the caster ignored, so
     * the server and the client aim at the same point.
     *
     * @param caster the entity whose look is traced
     * @return the clip
     */
    public static LookClip blocksAndLivingSeenBy(Entity caster) {
        return (from, to) -> {
            Vec3 stop = caster.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, caster)).getLocation();
            AABB swept = new AABB(from, stop).inflate(1);
            EntityHitResult living = ProjectileUtil.getEntityHitResult(caster, from, stop, swept,
                    candidate -> candidate instanceof LivingEntity alive && alive.isAlive(), from.distanceToSqr(stop));
            return living == null ? stop : living.getLocation();
        };
    }

    /**
     * The point the look intersects: the first point the clip meets along the
     * look from the eye inside the range, or the look's point at the range's
     * end.
     *
     * @param eye   the caster's eye
     * @param look  the caster's look, any length
     * @param range the ability's range in blocks
     * @param clip  what the look can meet
     * @return the point the ability goes to
     */
    public static Vec3 target(Vec3 eye, Vec3 look, double range, LookClip clip) {
        return clip.firstPoint(eye, eye.add(look.normalize().scale(range)));
    }

    /**
     * The unit direction from a source to the point the look intersects.
     *
     * @param source where the ability leaves, the lifted blob or the hand
     * @param eye    the caster's eye
     * @param look   the caster's look, any length
     * @param range  the ability's range in blocks
     * @param clip   what the look can meet
     * @return the unit heading, the look itself for a source standing on its target
     */
    public static Vec3 heading(Vec3 source, Vec3 eye, Vec3 look, double range, LookClip clip) {
        Vec3 line = target(eye, look, range, clip).subtract(source);
        return line.lengthSqr() < MIN_AIM_LENGTH_SQUARED ? look.normalize() : line.normalize();
    }
}

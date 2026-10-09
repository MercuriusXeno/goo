package com.mercuriusxeno.goo.ability.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Slows time inside a chronosphere: every non-player living entity and
 * every projectile keeps only a share of its motion each tick, and a living
 * one wears an unseen slowness as deep as the share, so a mob's own stride
 * crawls too; players pass through untouched.
 * chronosphere-hastes-players-slows-mobs
 */
public final class TimeVeil {

    /** Ticks the slowness outlasts the veil's last touch, so it holds between ticks and ends soon after. */
    static final int SLOWNESS_HOLD_TICKS = 3;
    /** The speed a level of slowness takes away. */
    private static final double SLOWNESS_PER_LEVEL = 0.15;
    /** Slack under a whole number of slowness levels, so a share landing on one does not round up past it. */
    private static final double LEVEL_SLACK = 1e-9;
    private static final int MAX_AMPLIFIER = 255;

    private TimeVeil() {
    }

    /**
     * Answers whether the veil slows an entity: a living entity or a
     * projectile, never a player.
     *
     * @param entity the entity
     * @return true when the veil slows it
     */
    static boolean slows(Entity entity) {
        return !(entity instanceof Player) && (entity instanceof LivingEntity || entity instanceof Projectile);
    }

    /**
     * The slowness amplifier that leaves a living entity about the share of
     * its speed the veil leaves its motion: each level takes 15 percent.
     *
     * @param slow the share of its motion kept, 0 to 1
     * @return the amplifier, zero for the lightest slowness
     */
    static int slownessAmplifier(double slow) {
        int levels = (int) Math.ceil((1.0 - slow) / SLOWNESS_PER_LEVEL - LEVEL_SLACK);
        return Math.clamp(levels - 1, 0, MAX_AMPLIFIER);
    }

    /**
     * Slows what the veil holds this tick.
     *
     * @param level  the level
     * @param center the veil's center
     * @param radius the veil's radius in blocks
     * @param slow   the share of its motion each keeps, 0 to 1
     */
    public static void slowWithin(ServerLevel level, Vec3 center, double radius, double slow) {
        if (radius <= 0) {
            return;
        }
        double radiusSquared = radius * radius;
        AABB reach = new AABB(center, center).inflate(radius);
        int amplifier = slownessAmplifier(slow);
        for (Entity entity : level.getEntities((Entity) null, reach,
                found -> slows(found) && found.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSquared)) {
            entity.setDeltaMovement(entity.getDeltaMovement().scale(slow));
            entity.hurtMarked = true;
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_HOLD_TICKS, amplifier,
                        true, false, false));
            }
        }
    }
}

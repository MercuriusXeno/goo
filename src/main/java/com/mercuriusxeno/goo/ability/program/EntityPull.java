package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * The world side of {@link EntityScanHost#pullEntitiesWithin}, shared by every
 * host that pulls: each living entity in the sphere is pushed toward the
 * center at a fixed speed, so its knockback resistance still applies,
 * and one already at the center is left still.
 */
final class EntityPull {

    private static final double MIN_DISTANCE_SQUARED = 0.25;

    private EntityPull() {
    }

    /**
     * Pulls the living entities within a sphere toward its center.
     *
     * @param level  the level to scan
     * @param center the sphere center
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center, in blocks per tick
     * @param self   the entity the pull centers on, null on a block
     */
    static void pullWithin(ServerLevel level, Vec3 center, double radius, double speed, @Nullable Entity self) {
        EntityScan.forEachLivingWithin(level, center, SelectionShape.SPHERE, radius, Set.of(), self,
                living -> pullToward(living, center, speed));
    }

    /**
     * Pushes one entity toward the center unless it already stands there.
     *
     * @param living the entity
     * @param center the center
     * @param speed  the velocity added toward the center
     */
    private static void pullToward(LivingEntity living, Vec3 center, double speed) {
        Vec3 toward = center.subtract(living.position());
        double distanceSquared = toward.lengthSqr();
        if (distanceSquared < MIN_DISTANCE_SQUARED) {
            return;
        }
        Vec3 push = toward.scale(speed / Math.sqrt(distanceSquared));
        living.push(push.x(), push.y(), push.z());
        living.hurtMarked = true;
    }
}

package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;

/**
 * The world side of {@link StepHost#anyEntityWithin}: the box scan, the
 * sphere trim and the meaning of each {@link EntityFilter}, shared by
 * every host that scans a level.
 */
final class EntityScan {

    private static final int DIAMETER_PER_RADIUS = 2;

    private EntityScan() {
    }

    /**
     * Scans the volume around a center for an entity every filter keeps.
     *
     * @param level   the level to scan
     * @param center  the volume center
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @return true when at least one entity is in the volume
     */
    static boolean anyEntityWithin(ServerLevel level, Vec3 center, SelectionShape shape,
                                   double radius, Set<EntityFilter> filters) {
        double diameter = radius * DIAMETER_PER_RADIUS;
        List<Entity> candidates = level.getEntities(null, AABB.ofSize(center, diameter, diameter, diameter));
        for (Entity entity : candidates) {
            if (inShape(entity, shape, center, radius) && passes(entity, filters)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether the entity lies within the shape; the box scan already
     * bounds the cube, so only the sphere trims further.
     *
     * @param entity the candidate
     * @param shape  the volume shape
     * @param center the anchor center
     * @param radius the volume radius
     * @return true when the entity is inside
     */
    private static boolean inShape(Entity entity, SelectionShape shape, Vec3 center, double radius) {
        return shape == SelectionShape.CUBE || entity.position().distanceTo(center) <= radius;
    }

    /**
     * Tests the entity against every filter.
     *
     * @param entity  the candidate
     * @param filters the filters to pass
     * @return true when every filter keeps the entity
     */
    private static boolean passes(Entity entity, Set<EntityFilter> filters) {
        for (EntityFilter filter : filters) {
            if (!keeps(filter, entity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Applies one filter, the world-side meaning of each name.
     *
     * @param filter the filter
     * @param entity the candidate
     * @return true when the filter keeps the entity
     */
    private static boolean keeps(EntityFilter filter, Entity entity) {
        return switch (filter) {
            case LIVING -> entity instanceof LivingEntity;
            case NOT_ITEM -> !(entity instanceof ItemEntity);
        };
    }
}

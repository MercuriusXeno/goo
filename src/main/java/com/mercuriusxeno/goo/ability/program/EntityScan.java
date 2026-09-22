package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * The world side of {@link StepHost#anyEntityWithin} and
 * {@link StepHost#forEachEntityWithin}: the box scan, the sphere trim and
 * the meaning of each {@link EntityFilter}, shared by every host that
 * scans a level. A scan carries the entity it centers on as self, null
 * on a block, so {@code not_target} can spare it.
 */
final class EntityScan {

    private static final int DIAMETER_PER_RADIUS = 2;
    private static final String ERR_UNMEANT_FILTER = "EntityScan gives no meaning to filter ";
    private static final Map<EntityFilter, BiPredicate<Entity, @Nullable Entity>> MEANINGS = meanings();

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
     * @param self    the entity the scan centers on, null on a block
     * @return true when at least one entity is in the volume
     */
    static boolean anyEntityWithin(ServerLevel level, Vec3 center, SelectionShape shape,
                                   double radius, Set<EntityFilter> filters, @Nullable Entity self) {
        return !select(level, center, shape, radius, filters, self).isEmpty();
    }

    /**
     * Hands the body each living entity within the volume that every
     * filter keeps, the world side of {@link StepHost#forEachEntityWithin}.
     *
     * @param level   the level to scan
     * @param center  the volume center
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @param self    the entity the scan centers on, null on a block
     * @param body    what to run on each living entity
     */
    static void forEachLivingWithin(ServerLevel level, Vec3 center, SelectionShape shape, double radius,
                                    Set<EntityFilter> filters, @Nullable Entity self, Consumer<LivingEntity> body) {
        for (Entity entity : select(level, center, shape, radius, filters, self)) {
            if (entity instanceof LivingEntity living) {
                body.accept(living);
            }
        }
    }

    /**
     * Collects the entities within the volume that every filter keeps.
     *
     * @param level   the level to scan
     * @param center  the volume center
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @param self    the entity the scan centers on, null on a block
     * @return the entities kept, in scan order
     */
    private static List<Entity> select(ServerLevel level, Vec3 center, SelectionShape shape,
                                       double radius, Set<EntityFilter> filters, @Nullable Entity self) {
        double diameter = radius * DIAMETER_PER_RADIUS;
        List<Entity> candidates = level.getEntities(null, AABB.ofSize(center, diameter, diameter, diameter));
        List<Entity> kept = new ArrayList<>();
        for (Entity entity : candidates) {
            if (inShape(entity, shape, center, radius) && passes(entity, filters, self)) {
                kept.add(entity);
            }
        }
        return kept;
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
     * Tests the entity against every filter; the meaning of a host's
     * {@link StepHost#targetPasses} as well as the scan's own trim.
     *
     * @param entity  the candidate
     * @param filters the filters to pass
     * @param self    the entity the selection centers on, null on a block
     * @return true when every filter keeps the entity
     */
    static boolean passes(Entity entity, Set<EntityFilter> filters, @Nullable Entity self) {
        for (EntityFilter filter : filters) {
            if (!MEANINGS.get(filter).test(entity, self)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Builds the table of what each filter keeps, refusing at class load
     * when a filter has no meaning here. Each predicate reads the
     * candidate and the entity the selection centers on.
     *
     * @return the table, one predicate per filter
     */
    private static Map<EntityFilter, BiPredicate<Entity, @Nullable Entity>> meanings() {
        Map<EntityFilter, BiPredicate<Entity, @Nullable Entity>> table = new EnumMap<>(EntityFilter.class);
        table.put(EntityFilter.LIVING, (entity, self) -> entity instanceof LivingEntity);
        table.put(EntityFilter.NOT_ITEM, (entity, self) -> !(entity instanceof ItemEntity));
        table.put(EntityFilter.NOT_BOSS, (entity, self) -> !isBoss(entity));
        table.put(EntityFilter.MOB, (entity, self) -> entity instanceof Mob);
        table.put(EntityFilter.NOT_FIRE_IMMUNE, (entity, self) -> !entity.fireImmune());
        table.put(EntityFilter.UNDEAD,
                (entity, self) -> entity instanceof LivingEntity living && living.isInvertedHealAndHarm());
        table.put(EntityFilter.ALIVE, (entity, self) -> entity.isAlive());
        table.put(EntityFilter.NOT_TARGET, (entity, self) -> entity != self);
        for (EntityFilter filter : EntityFilter.values()) {
            if (!table.containsKey(filter)) {
                throw new IllegalStateException(ERR_UNMEANT_FILTER + filter);
            }
        }
        return table;
    }

    /**
     * Tests whether the entity is a wither or an ender dragon, the two
     * bosses the mob abilities leave alone.
     *
     * @param entity the candidate
     * @return true for a boss
     */
    private static boolean isBoss(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
    }
}

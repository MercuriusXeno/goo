package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The {@link StepHost} over a chain marker block entity: reads stack
 * count, placed face and blob shape from the block entity, and acts on
 * the server level at the marker position. Built fresh each tick from
 * what the {@link com.mercuriusxeno.goo.ability.ChainBehavior} callbacks
 * hand over, so it holds no state of its own.
 *
 * @param level the server level
 * @param pos   the marker position
 * @param be    the marker block entity
 */
public record MarkerHost(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) implements StepHost {

    /**
     * Variable naming the live stack count.
     */
    public static final String VAR_STACKS = "stacks";
    /**
     * Variable naming the stack ceiling.
     */
    public static final String VAR_MAX_STACKS = "max_stacks";
    /**
     * Variable reading one for a flat blob and zero otherwise.
     */
    public static final String VAR_FLAT = "flat";
    /**
     * The variables this host binds.
     */
    public static final Set<String> VARIABLES = Set.of(VAR_STACKS, VAR_MAX_STACKS, VAR_FLAT);

    private static final int DIAMETER_PER_RADIUS = 2;

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case VAR_STACKS -> OptionalDouble.of(be.getStackCount());
            case VAR_MAX_STACKS -> OptionalDouble.of(be.getMaxStacks());
            case VAR_FLAT -> OptionalDouble.of(be.isFlatBlob() ? 1 : 0);
            default -> OptionalDouble.empty();
        };
    }

    @Override
    public BlockPos position() {
        return pos;
    }

    @Override
    public Direction placedFace() {
        return be.getPlacedFace();
    }

    @Override
    public int stackCount() {
        return be.getStackCount();
    }

    @Override
    public void decrementStack() {
        be.decrementStack();
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        Vec3 center = Vec3.atCenterOf(pos);
        Level.ExplosionInteraction interaction = mode == ExplosionMode.TNT
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(null, center.x(), center.y(), center.z(), power, interaction);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        Vec3 center = Vec3.atCenterOf(pos);
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

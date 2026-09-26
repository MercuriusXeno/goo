package com.mercuriusxeno.goo.ability.program;

import java.util.Set;
import java.util.function.Consumer;

/**
 * A host that scans the entities around its anchor (capability
 * {@link HostCapability#ENTITY_SCAN}). Each body it hands an entity to
 * receives a {@link TargetHost} bound to that entity.
 */
public interface EntityScanHost extends StepHost {

    /**
     * Scans the volume around the anchor for an entity every filter keeps.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @return true when at least one entity is in the volume
     */
    boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters);

    /**
     * Scans the volume around the anchor and hands the body a host bound
     * to each living entity every filter keeps, so the body's steps act on
     * that entity as their target.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @param body    what to run on the host bound to each entity
     */
    void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                             Consumer<TargetHost> body);

    /**
     * Hands the body a host bound to the living entity with this id, when
     * one still stands in the host's level; a strike chosen ticks ago lands
     * on its entity this way.
     *
     * @param entityId the entity's id in the level
     * @param body     what to run on the host bound to the entity
     */
    void forEntity(int entityId, Consumer<TargetHost> body);

    /**
     * Pulls every living entity within a sphere around the anchor toward
     * the anchor's center.
     *
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center, in blocks per tick
     */
    void pullEntitiesWithin(double radius, double speed);
}

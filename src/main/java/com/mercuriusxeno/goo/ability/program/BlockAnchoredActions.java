package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The world actions a host anchored at a block shares whatever block it
 * is, the chain marker or a tap's landing: entity scans handing each body
 * an entity host with no thrower, and particle bursts spread along the
 * anchor's axis.
 */
final class BlockAnchoredActions {

    private BlockAnchoredActions() {
    }

    /**
     * Hands the body an entity host for each living entity in the volume
     * that every filter keeps.
     *
     * @param level   the server level
     * @param center  the volume's center
     * @param shape   the volume shape
     * @param radius  the volume radius
     * @param filters the filters an entity must pass
     * @param body    what to run on each entity's host
     */
    static void forEachEntityWithin(ServerLevel level, Vec3 center, SelectionShape shape, double radius,
                                    Set<EntityFilter> filters, Consumer<TargetHost> body) {
        EntityScan.forEachLivingWithin(level, center, shape, radius, filters, null,
                living -> body.accept(new EntityHost(level, living, null)));
    }

    /**
     * Hands the body an entity host for the living entity with this id, when
     * one still stands in the level.
     *
     * @param level    the server level
     * @param entityId the entity's id
     * @param body     what to run on the entity's host
     */
    static void forEntity(ServerLevel level, int entityId, Consumer<TargetHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, null));
        }
    }

    /**
     * Sends a particle burst centered above the anchor, spread along the
     * anchor's axis by the burst's along spread and across it by the other.
     *
     * @param level  the server level
     * @param center the anchor
     * @param along  the anchor's axis
     * @param burst  the burst
     */
    static void sendBurst(ServerLevel level, Vec3 center, Direction.Axis along, ParticleBurst burst) {
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                center.x(), center.y() + burst.lift(), center.z(), burst.count(),
                spreadOn(Direction.Axis.X, along, burst), spreadOn(Direction.Axis.Y, along, burst),
                spreadOn(Direction.Axis.Z, along, burst), burst.speed()));
    }

    /**
     * Picks the burst's spread for one axis: along where the axis is the
     * anchor's, across otherwise.
     *
     * @param axis  the axis to spread on
     * @param along the anchor's axis
     * @param burst the burst
     * @return the spread on the axis
     */
    private static double spreadOn(Direction.Axis axis, Direction.Axis along, ParticleBurst burst) {
        return axis == along ? burst.spreadAlong() : burst.spreadAcross();
    }
}

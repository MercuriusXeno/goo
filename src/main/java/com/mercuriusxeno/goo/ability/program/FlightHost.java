package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over a thrown blob in flight: the world around the
 * point it has reached this tick, the entities and still water and lava near
 * it, acted on each tick of the flight with no target and no driver of its
 * own. Frost's Orb freezes what it passes this way
 * (decision orb-carries-a-swirling-nova). Each entity it reaches is hosted
 * with the blob's thrower, so a hit on it names the player behind the blob
 * (decisions direct-goo-hit-credits-its-caster,
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it).
 *
 * @param level   the server level
 * @param point   where the blob is this tick
 * @param thrower the entity that threw the blob, or null when unknown
 */
public record FlightHost(ServerLevel level, Vec3 point, @Nullable Entity thrower)
        implements AnchoredWorldHost, FrostHost {

    @Override
    public HostKind kind() {
        return HostKind.FLIGHT;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return BlockPos.containing(point);
    }

    @Override
    public Vec3 anchor() {
        return point;
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public Vec3 frostCenter() {
        return point;
    }

    /**
     * Hands the body a host for each living entity in the volume, hosted
     * with the blob's thrower.
     * direct-goo-hit-credits-its-caster
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     */
    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        AnchoredWorldHost.super.forEachEntityWithin(shape, radius, filters, reached -> body.accept(thrownAt(reached)));
    }

    /**
     * Hands the body a host for the living entity with this id, hosted with
     * the blob's thrower.
     * direct-goo-hit-credits-its-caster
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     */
    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        AnchoredWorldHost.super.forEntity(entityId, reached -> body.accept(thrownAt(reached)));
    }

    /**
     * The host of an entity the blob reached, naming the blob's thrower.
     *
     * @param reached the reached entity's host
     * @return the host naming the thrower
     */
    private TargetHost thrownAt(TargetHost reached) {
        return new EntityHost(level, reached.target(), thrower);
    }
}

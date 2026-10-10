package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over a thrown blob in flight: the world around the
 * point it has reached this tick, the entities and still water and lava near
 * it, acted on each tick of the flight with no target and no driver of its
 * own. Frost's Orb freezes what it passes this way
 * (decision orb-carries-a-swirling-nova).
 *
 * @param level the server level
 * @param point where the blob is this tick
 */
public record FlightHost(ServerLevel level, Vec3 point) implements AnchoredWorldHost, FrostHost {

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
}

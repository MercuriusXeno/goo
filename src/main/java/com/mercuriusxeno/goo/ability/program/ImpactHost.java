package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;

/**
 * The point a ray lands on, the mob or block face it strikes: the world
 * around that point, acted on in the hit tick, with no target and no driver
 * for later ticks.
 * decision sunbeam-lands-with-impact-and-aim
 *
 * @param level the server level
 * @param point where the ray lands
 * @param face  the face the ray strikes, which a burst spreads along
 */
public record ImpactHost(ServerLevel level, Vec3 point, Direction face) implements AnchoredWorldHost {

    @Override
    public Vec3 anchor() {
        return point;
    }

    @Override
    public Direction.Axis burstAxis() {
        return face.getAxis();
    }

    @Override
    public HostKind kind() {
        return HostKind.IMPACT;
    }

    @Override
    public BlockPos position() {
        return BlockPos.containing(point);
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }
}

package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireLift;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over a Spire's submitted footprint: the ground it
 * lifts, acted on in the tick the submit lands, its particles and sound
 * reaching out from the footprint's center.
 * decision spire-rips-walls-and-platforms
 *
 * @param level the server level
 * @param lift  the planned lift
 */
public record FootprintHost(ServerLevel level, SpireLift lift) implements RaiseGroundHost {

    private static final double HALF = 0.5;

    @Override
    public HostKind kind() {
        return HostKind.FOOTPRINT;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return lift.footprint().corner();
    }

    @Override
    public Vec3 anchor() {
        BlockPos min = lift.footprint().min();
        BlockPos max = lift.footprint().max();
        return new Vec3((min.getX() + max.getX() + 1) * HALF, min.getY() + 1.0, (min.getZ() + max.getZ() + 1) * HALF);
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }
}

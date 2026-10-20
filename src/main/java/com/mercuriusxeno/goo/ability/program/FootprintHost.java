package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over a Spire's submitted footprint: the stone it
 * grows, acted on in the tick the submit lands, its particles and sound
 * reaching out from the footprint's center.
 * decision spire-grows-stone-out-of-the-top
 *
 * @param level  the server level
 * @param growth the planned growth
 */
public record FootprintHost(ServerLevel level, SpireGrowth growth) implements RaiseGroundHost {

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
        return growth.footprint().corner();
    }

    @Override
    public Vec3 anchor() {
        BlockPos min = growth.footprint().min();
        BlockPos max = growth.footprint().max();
        return new Vec3((min.getX() + max.getX() + 1) * HALF, min.getY() + 1.0, (min.getZ() + max.getZ() + 1) * HALF);
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }
}

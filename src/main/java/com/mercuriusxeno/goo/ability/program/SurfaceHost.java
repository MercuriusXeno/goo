package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over one floor a spray reaches: a stream's cone or a
 * spore burst runs an ability's {@code on_blocks} steps on each floor block
 * it reaches, world actions anchoring at the floor's top face and a placed
 * block going into the empty cell above it. It has no target and nothing
 * ticks it afterwards
 * (decision mycosis-spore-stream-buds-and-poisons).
 *
 * <p>{@code distance} reads how far the floor's open cell sits from the
 * spray's source, so a burst can treat the floor it landed on apart from
 * the rest (decision colonize-blob-grows-the-network).
 *
 * @param level    the server level
 * @param floor    the floor block reached
 * @param distance how far the floor's open cell sits from the spray's source, in blocks
 */
public record SurfaceHost(ServerLevel level, BlockPos floor, double distance)
        implements AnchoredWorldHost, PlaceBlockHost {

    private static final double HALF = 0.5;

    /**
     * The center of a block's face.
     *
     * @param block the block
     * @param face  the face
     * @return the face center
     */
    public static Vec3 faceCenter(BlockPos block, Direction face) {
        return Vec3.atCenterOf(block).add(face.getStepX() * HALF, face.getStepY() * HALF, face.getStepZ() * HALF);
    }

    @Override
    public Vec3 anchor() {
        return faceCenter(floor, Direction.UP);
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public HostKind kind() {
        return HostKind.SURFACE;
    }

    @Override
    public OptionalDouble read(String name) {
        return HostVariables.DISTANCE.equals(name) ? OptionalDouble.of(distance) : OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return floor;
    }

    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        BlockAnchoredActions.placeBeyondFace(level, floor, Direction.UP, block, state);
    }
}

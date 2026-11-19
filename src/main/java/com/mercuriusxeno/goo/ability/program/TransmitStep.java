package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Moves the host's target onto the mycelial block it aims at within a range,
 * travelling through the mycelium it touches, and finishes. A target touching
 * no mycelial block, or aiming at one no path of mycelial blocks joins to the
 * blocks it touches, is not admitted, so the ability neither runs nor drains:
 * any break in the network is a place you can't go.
 * Transmit is {@code transmit range=64}.
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 *
 * @param range the reach of the aim and of the path, in blocks, evaluated when the step runs
 */
public record TransmitStep(Expr range) implements Step {

    private static final String NAME = "transmit";
    private static final String FIELD_RANGE = "range";
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<TransmitStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(TransmitStep::range)
    ).apply(inst, TransmitStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TransmitStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TransmitStep> type() {
        return TYPE;
    }

    @Override
    public boolean admits(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        return destination(target.level(), target, range.evaluate(context)).isPresent();
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        destination(target.level(), target, range.evaluate(context)).ifPresent(pos -> {
            Vec3 stand = standingOn(target.level(), pos);
            // ghost-trail-spans-the-blink: a step after the jump reads where the target left from
            target.setData(GooAttachments.JUMP_SOURCE, target.position());
            target.teleportTo(stand.x, stand.y, stand.z);
        });
        return true;
    }

    /**
     * The mycelial block an entity transmits onto: the one it aims at within
     * the range, where a path of mycelial blocks joins it to a block the
     * entity touches.
     *
     * @param level  the level
     * @param entity the transmitting entity
     * @param range  the reach of the aim and of the path, in blocks
     * @return the block, or empty where the entity touches no mycelium or aims at no block joined to it
     */
    public static Optional<BlockPos> destination(Level level, Entity entity, double range) {
        List<BlockPos> touched = MycelialTouch.touched(level, entity);
        if (touched.isEmpty()) {
            return Optional.empty();
        }
        return MyceliumAim.aimed(level, entity, range)
                .filter(aimed -> MyceliumPaths.connects(level, touched, aimed, range));
    }

    /**
     * Where an entity stands on a mycelial block: on top of its collision, or
     * in its cell for a block with none, such as a sculk vein.
     *
     * @param level the level
     * @param pos   the block
     * @return the standing point
     */
    public static Vec3 standingOn(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape collision = state.getCollisionShape(level, pos);
        double top = collision.isEmpty() ? pos.getY() : pos.getY() + collision.max(Direction.Axis.Y);
        return new Vec3(pos.getX() + HALF, top, pos.getZ() + HALF);
    }

    /**
     * The range of the first transmit step in a program whose range reads no
     * variable, which the client's marker aims with.
     *
     * @param behaviors the program
     * @return the range, or empty when the program transmits at no fixed range
     */
    public static OptionalDouble transmitRange(List<Step> behaviors) {
        for (Step step : behaviors) {
            if (step instanceof TransmitStep transmit && transmit.range().variables().isEmpty()) {
                return OptionalDouble.of(transmit.range().evaluate(Variables.NONE));
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(range);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}

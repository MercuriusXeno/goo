package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Drops the host's target into a random cave within the radius, favoring
 * depths below it: {@code dive radius=16}. A cave cell is one the sky cannot
 * see, standing on a sturdy floor, holding no fluid at the feet or the head,
 * where the target's body fits clear of every block, the check a blink's
 * landing makes. With no such cell in reach the dive neither runs nor drains.
 * decision dive-drops-you-to-a-cave-below
 *
 * @param radius how far from the target's feet a landing may lie, evaluated when the step runs
 */
public record DiveStep(Expr radius) implements Step {

    private static final String NAME = "dive";
    private static final String FIELD_RADIUS = "radius";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DiveStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(DiveStep::radius)
    ).apply(inst, DiveStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DiveStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DiveStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity diver = context.hostAs(TargetHost.class).target();
        landing(diver, radius.evaluateInt(context)).ifPresent(feet -> {
            // ghost-trail-spans-the-blink: a ghost trail after the dive reads where the diver left from
            diver.setData(GooAttachments.JUMP_SOURCE, diver.position());
            Vec3 at = Vec3.atBottomCenterOf(feet);
            diver.teleportTo(at.x(), at.y(), at.z());
        });
        return true;
    }

    /**
     * A dive with no cave in reach neither runs nor drains.
     */
    @Override
    public boolean admits(StepContext context) {
        return landing(context.hostAs(TargetHost.class).target(), radius.evaluateInt(context)).isPresent();
    }

    private static Optional<BlockPos> landing(LivingEntity diver, int reach) {
        Level level = diver.level();
        return DiveLandings.pick(diver.blockPosition(), reach, feet -> inCave(level, diver, feet), level.getRandom());
    }

    /**
     * Whether a diver can stand with its feet in a cell of a cave.
     *
     * @param level the diver's level
     * @param diver the diving entity, whose own box never blocks it
     * @param feet  the cell its feet would stand in
     * @return true for a cell the sky cannot see, on a sturdy floor, dry, where the diver fits
     */
    static boolean inCave(Level level, LivingEntity diver, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && isDry(level, feet)
                && !level.canSeeSky(feet)
                && new LevelBlinkSpace(level, diver).fits(BlinkBody.of(diver).boxAt(Vec3.atBottomCenterOf(feet)));
    }

    private static boolean isDry(Level level, BlockPos feet) {
        return level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}

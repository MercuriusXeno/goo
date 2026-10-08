package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Moves the host's target onto the fungus block it aims at within a range,
 * from where it stands near another fungus, and finishes; a target standing
 * near no fungus, or aiming at no fungus within the range, is not admitted,
 * so the ability neither runs nor drains.
 * Fungal Shift is {@code shift range=64}
 * (decision fungal-shift-blinks-to-the-aimed-fungus).
 *
 * @param range the reach of the aim in blocks, evaluated when the step runs
 * @param near  how near a fungus the target must stand to shift, evaluated when the step runs
 */
public record ShiftStep(Expr range, Expr near) implements Step {

    /** The blocks a shift lands on: mushrooms, fungi, nylium, mycelium, shroomlight, mushroom blocks. */
    public static final TagKey<Block> FUNGUS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, "fungus"));

    private static final String NAME = "shift";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_NEAR = "near";
    /** How near a fungus the shifter must stand where the JSON names no reach. */
    private static final double DEFAULT_NEAR = 3;
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ShiftStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(ShiftStep::range),
            Expr.CODEC.optionalFieldOf(FIELD_NEAR, Expr.literal(DEFAULT_NEAR)).forGetter(ShiftStep::near)
    ).apply(inst, ShiftStep::new));

    /**
     * A shift at a range, from within the default reach of a fungus.
     *
     * @param range the reach of the aim in blocks
     */
    public ShiftStep(Expr range) {
        this(range, Expr.literal(DEFAULT_NEAR));
    }

    /**
     * The registered type.
     */
    public static final StepType<ShiftStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShiftStep> type() {
        return TYPE;
    }

    @Override
    public boolean admits(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        return FungusAim.standsNearFungus(target.level(), target, near.evaluate(context))
                && aimedFungus(target.level(), target, reachOf(target, range.evaluate(context))).isPresent();
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (!FungusAim.standsNearFungus(target.level(), target, near.evaluate(context))) {
            return true;
        }
        aimedFungus(target.level(), target, reachOf(target, range.evaluate(context))).ifPresent(stand -> {
            // ghost-trail-spans-the-blink: a step after the jump reads where the target left from
            target.setData(GooAttachments.JUMP_SOURCE, target.position());
            target.teleportTo(stand.x, stand.y, stand.z);
        });
        return true;
    }

    /**
     * How far an entity's shift reaches: the range, times the factor of the
     * fungal sight it holds (decision sight-lengthens-shift-and-outlines-fungus).
     *
     * @param entity the shifting entity
     * @param range  the step's range
     * @return the reach in blocks
     */
    public static double reachOf(Entity entity, double range) {
        return range * entity.getData(GooAttachments.SIGHT).factorAt(entity.level().getGameTime());
    }

    /**
     * Where an entity stands after shifting onto the fungus block it aims at
     * within the reach, as {@link FungusAim} names it: on top of the block's
     * collision, or in its cell for a fungus with none, such as a mushroom.
     *
     * @param level  the level
     * @param entity the aiming entity
     * @param reach  the aim's reach in blocks
     * @return the standing point, or empty when no fungus is aimed at
     */
    public static Optional<Vec3> aimedFungus(Level level, Entity entity, double reach) {
        return FungusAim.aimedFungus(level, entity, reach).map(pos -> standingOn(level, pos));
    }

    /**
     * Where an entity stands on a fungus block: on top of its collision, or
     * in its cell for a fungus with none, such as a mushroom.
     *
     * @param level the level
     * @param pos   the fungus block
     * @return the standing point
     */
    public static Vec3 standingOn(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape collision = state.getCollisionShape(level, pos);
        double top = collision.isEmpty() ? pos.getY() : pos.getY() + collision.max(Direction.Axis.Y);
        return new Vec3(pos.getX() + HALF, top, pos.getZ() + HALF);
    }

    /**
     * How near a fungus the first shift step in a program needs its shifter,
     * where that reach reads no variable, which the client's marker checks with.
     *
     * @param behaviors the program
     * @return the reach, or empty when the program shifts from no fixed reach
     */
    public static OptionalDouble fungusNear(List<Step> behaviors) {
        for (Step step : behaviors) {
            if (step instanceof ShiftStep shift && shift.near().variables().isEmpty()) {
                return OptionalDouble.of(shift.near().evaluate(Variables.NONE));
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * The range of the first shift step in a program whose range reads no
     * variable, which the client's marker aims with.
     *
     * @param behaviors the program
     * @return the range, or empty when the program shifts at no fixed range
     */
    public static OptionalDouble fungusRange(List<Step> behaviors) {
        for (Step step : behaviors) {
            if (step instanceof ShiftStep shift && shift.range().variables().isEmpty()) {
                return OptionalDouble.of(shift.range().evaluate(Variables.NONE));
            }
        }
        return OptionalDouble.empty();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(range, near);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}

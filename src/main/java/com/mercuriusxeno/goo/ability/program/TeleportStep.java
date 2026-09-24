package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Moves the host's target and finishes. Ender teleport is
 * {@code teleport mode=random_offset range=32}: a random horizontal jump
 * of up to sixteen blocks either way.
 *
 * @param mode  how the destination is picked
 * @param range the mode's range in blocks, evaluated when the step runs
 */
public record TeleportStep(TeleportMode mode, Expr range) implements Step {

    private static final String NAME = "teleport";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_RANGE = "range";
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<TeleportStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TeleportMode.CODEC.fieldOf(FIELD_MODE).forGetter(TeleportStep::mode),
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(TeleportStep::range)
    ).apply(inst, TeleportStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TeleportStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TeleportStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.host().target();
        double reach = range.evaluate(context);
        Vec3 jump = switch (mode) {
            case RANDOM_OFFSET -> randomOffset(target, reach);
            case TOWARD_THROWER -> towardThrower(target, context.host().thrower(), reach);
            case AWAY_FROM_THROWER -> towardThrower(target, context.host().thrower(), -reach);
        };
        target.teleportTo(target.getX() + jump.x(), target.getY(), target.getZ() + jump.z());
        return true;
    }

    /**
     * Rolls a level jump of up to half the range either way on each axis.
     *
     * @param target the entity jumping
     * @param range  the full width of the roll
     * @return the jump
     */
    private static Vec3 randomOffset(LivingEntity target, double range) {
        RandomSource random = target.level().getRandom();
        return new Vec3((random.nextDouble() - HALF) * range, 0, (random.nextDouble() - HALF) * range);
    }

    /**
     * Measures a level jump of the range along the line from the target
     * to the thrower; a negative range jumps away.
     *
     * @param target  the entity jumping
     * @param thrower the entity the line runs to, or null when unknown
     * @param range   the jump length, negative to jump away
     * @return the jump, zero with no thrower or a thrower at the target
     */
    private static Vec3 towardThrower(LivingEntity target, @Nullable Entity thrower, double range) {
        if (thrower == null) {
            return Vec3.ZERO;
        }
        Vec3 line = new Vec3(thrower.getX() - target.getX(), 0, thrower.getZ() - target.getZ());
        return line.lengthSqr() == 0 ? Vec3.ZERO : line.normalize().scale(range);
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

package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.OptionalDouble;
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
        LivingEntity target = context.hostAs(TargetHost.class).target();
        double reach = range.evaluate(context);
        Vec3 standing = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 destination = switch (mode) {
            case RANDOM_OFFSET -> standing.add(randomOffset(target, reach));
            case TOWARD_THROWER ->
                    standing.add(towardThrower(target, context.hostAs(TargetHost.class).thrower(), reach));
            case AWAY_FROM_THROWER ->
                    standing.add(towardThrower(target, context.hostAs(TargetHost.class).thrower(), -reach));
            case THROWER_LOOK -> alongLook(standing, context.hostAs(TargetHost.class).thrower(), reach);
        };
        // A step after the jump, a ghost trail, reads where the target left from: a player's old
        // position is overwritten by the teleport itself, so the source is kept on the entity.
        // Decision ghost-trail-spans-the-blink.
        target.setData(GooAttachments.JUMP_SOURCE, target.position());
        target.teleportTo(destination.x(), destination.y(), destination.z());
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

    /**
     * The point a jump of the range along the thrower's look lands on.
     *
     * @param standing where the target stands
     * @param thrower  the entity whose look the jump follows, or null when unknown
     * @param range    the jump length
     * @return the landing point, where the target stands with no thrower
     */
    private static Vec3 alongLook(Vec3 standing, @Nullable Entity thrower, double range) {
        return thrower == null ? standing : lookDestination(standing, thrower.getLookAngle(), range);
    }

    /**
     * The point a blink along a look lands on. The server's teleport and the
     * client's blink cursor both resolve the destination here, so the cursor
     * stands where the jump lands; a safety check on the landing belongs here.
     * Decision ripple-outline-is-the-blink-cursor.
     *
     * @param standing where the blinking entity stands, its feet
     * @param look     the unit look vector
     * @param range    the jump length in blocks
     * @return the landing point
     */
    public static Vec3 lookDestination(Vec3 standing, Vec3 look, double range) {
        return standing.add(look.scale(range));
    }

    /**
     * The range of the first look-following teleport among the steps, for a
     * client that previews where it lands. A range naming a variable reads
     * only when the step runs, so it answers nothing here.
     * Decision ripple-outline-is-the-blink-cursor.
     *
     * @param behaviors an ability's top-level steps
     * @return the range, empty where no look teleport with a literal range stands
     */
    public static OptionalDouble lookRange(List<Step> behaviors) {
        for (Step step : behaviors) {
            if (step instanceof TeleportStep teleport && teleport.mode() == TeleportMode.THROWER_LOOK
                    && teleport.range().variables().isEmpty()) {
                return OptionalDouble.of(teleport.range().evaluate(Variables.NONE));
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

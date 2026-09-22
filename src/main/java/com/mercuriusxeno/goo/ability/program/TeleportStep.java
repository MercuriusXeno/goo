package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
        context.host().teleportTarget(mode, range.evaluate(context));
        return true;
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

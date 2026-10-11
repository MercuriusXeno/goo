package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Leaves a bouncy pad where the blob lands: a goo block that cancels fall
 * damage and launches what lands on it back up, gone after its time.
 * weird-bounces-and-softens-harm
 *
 * @param ticks how long the pad stands, evaluated on the host
 */
public record BouncePadStep(Expr ticks) implements Step {

    private static final String NAME = "bounce_pad";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BouncePadStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_TICKS).forGetter(BouncePadStep::ticks)
    ).apply(inst, BouncePadStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BouncePadStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BouncePadStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(TimedBlockHost.class).placeForTicks(GooBlocks.BOUNCE_PAD.get(), ticks.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(ticks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TIMED_BLOCK);
    }
}

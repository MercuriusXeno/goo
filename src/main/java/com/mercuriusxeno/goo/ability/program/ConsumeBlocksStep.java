package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Removes every block with a goo value within a sphere around the host
 * anchor, adding each block's goo to the total the host keeps, and
 * finishes; blocks without a value stand. The nether black hole consumes
 * its blast sphere as it leaves its expand phase:
 * {@code consume_blocks radius="1 + 2 * stacks"}.
 *
 * @param radius the sphere radius in whole blocks, evaluated when the step runs
 */
public record ConsumeBlocksStep(Expr radius) implements Step {

    private static final String NAME = "consume_blocks";
    private static final String FIELD_RADIUS = "radius";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ConsumeBlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(ConsumeBlocksStep::radius)
    ).apply(inst, ConsumeBlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ConsumeBlocksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ConsumeBlocksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.host().consumeValuedBlocks(radius.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CONSUMED_GOO);
    }
}

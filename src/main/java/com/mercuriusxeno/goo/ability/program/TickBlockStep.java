package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ticks the block the host reaches faster: its block entity's ticker runs
 * the JSON's extra ticks on each tick the step runs. Aeon Tick streams it on
 * the aimed machine: {@code tick_block extra_ticks=4}.
 * tick-channel-marches-squares-on-the-face
 *
 * @param extraTicks the extra ticks the block takes each time the step runs
 */
public record TickBlockStep(int extraTicks) implements Step {

    private static final String NAME = "tick_block";
    private static final String FIELD_EXTRA_TICKS = "extra_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<TickBlockStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_EXTRA_TICKS).forGetter(TickBlockStep::extraTicks)
    ).apply(inst, TickBlockStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TickBlockStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TickBlockStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TickBlockHost host = context.hostAs(TickBlockHost.class);
        host.tickedBlock().ifPresent(pos -> host.tickBlock(pos, extraTicks));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICK_BLOCK);
    }
}

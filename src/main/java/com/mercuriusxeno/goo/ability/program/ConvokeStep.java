package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Convokes a mob from the host's chunk to its spot, trying once every
 * period: finishes the try a mob arrives, and stays inert on each try none
 * does. The convoke blob lingers on
 * {@code convoke every=20} until a mob comes; a tap's single drip tries once.
 * Decisions convoke-blob-throbs-until-a-mob-arrives and convoke-drip-rolls-a-small-chance.
 *
 * @param every the ticks between tries, evaluated each tick; one tries every tick
 */
public record ConvokeStep(Expr every) implements Step {

    private static final String NAME = "convoke";
    private static final String FIELD_EVERY = "every";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ConvokeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_EVERY, Expr.literal(1)).forGetter(ConvokeStep::every)
    ).apply(inst, ConvokeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ConvokeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ConvokeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ConvokeHost host = context.hostAs(ConvokeHost.class);
        return triesOn(host.gameTime(), every.evaluateInt(context)) && host.convokeFromChunk();
    }

    /**
     * Whether a game time is one a convoke tries on.
     *
     * @param gameTime the game time
     * @param period   the ticks between tries
     * @return true on each multiple of the period, every tick for a period of one or less
     */
    public static boolean triesOn(long gameTime, int period) {
        return period <= 1 || gameTime % period == 0;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(every);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CONVOKE);
    }
}

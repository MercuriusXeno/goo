package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Banks ticks on the prism it runs on, each tick for as long as the prism
 * stands: the timekeeper combo's whole program. Tick streamed on the prism
 * then spends up to {@code spend_per_tick} of the bank a held tick moving
 * the clock forward.
 * timekeeper-prism-banks-ticks-forward-only
 *
 * @param perTick      the charge standing banks a tick
 * @param spendPerTick the most charge one held tick of Tick spends
 */
public record BankTicksStep(int perTick, int spendPerTick) implements Step {

    private static final String NAME = "bank_ticks";
    private static final String FIELD_PER_TICK = "per_tick";
    private static final String FIELD_SPEND_PER_TICK = "spend_per_tick";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BankTicksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_PER_TICK).forGetter(BankTicksStep::perTick),
            Codec.INT.fieldOf(FIELD_SPEND_PER_TICK).forGetter(BankTicksStep::spendPerTick)
    ).apply(inst, BankTicksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BankTicksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BankTicksStep> type() {
        return TYPE;
    }

    /**
     * Banks this tick and never finishes, so the prism banks while it stands.
     */
    @Override
    public boolean tick(StepContext context) {
        context.hostAs(TickBankHost.class).bankTicks(perTick, spendPerTick);
        return false;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICK_BANK, HostCapability.TICKING);
    }
}

package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Withdraws the standing charge banked in the block a held stream ends on,
 * as goo into the streaming player's holdings: Rewind streamed on a
 * timekeeper prism draws out the aeon it stored by standing.
 * timekeeper-prism-banks-ticks-forward-only
 *
 * @param mbPerTick   the most mB withdrawn a held tick
 * @param chargePerMb the standing charge one mB is worth
 */
public record WithdrawBankStep(int mbPerTick, int chargePerMb) implements Step {

    private static final String NAME = "withdraw_bank";
    private static final String FIELD_MB_PER_TICK = "mb_per_tick";
    private static final String FIELD_CHARGE_PER_MB = "charge_per_mb";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WithdrawBankStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_MB_PER_TICK).forGetter(WithdrawBankStep::mbPerTick),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_CHARGE_PER_MB).forGetter(WithdrawBankStep::chargePerMb)
    ).apply(inst, WithdrawBankStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WithdrawBankStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WithdrawBankStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TickBlockHost host = context.hostAs(TickBlockHost.class);
        host.tickedBlock().ifPresent(pos -> host.withdrawBank(pos, mbPerTick, chargePerMb));
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

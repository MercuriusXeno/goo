package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Ender's End, run each held tick of the channel: each held tick stands the
 * gate the hold conjures before the player, as Zone's door stands, and the
 * tick the hold reaches its charge carries them through it to where it
 * leads. A hold the gate leads nowhere from stands nothing. End is
 * {@code end charge=20}.
 * decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back
 * decision end-clears-blocks-and-opens-a-portal
 *
 * @param charge the held ticks the channel asks before it carries the caster
 */
public record EndStep(Expr charge) implements Step {

    private static final String NAME = "end";
    private static final String FIELD_CHARGE = "charge";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<EndStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHARGE).forGetter(EndStep::charge)
    ).apply(inst, EndStep::new));

    /**
     * The registered type.
     */
    public static final StepType<EndStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<EndStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        EndHost host = context.hostAs(EndHost.class);
        int held = host.channelAim().map(ChannelAim::held).orElse(0);
        int charged = charge.evaluateInt(context);
        if (held <= 0 || held > charged) {
            return true;
        }
        host.endDestination().ifPresent(destination -> {
            host.conjureEndGate(held, charged);
            if (held == charged) {
                host.carryThroughEndGate(destination);
            }
        });
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(charge);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.END);
    }
}

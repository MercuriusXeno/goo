package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Void's Outsider, run each held tick of the channel: the tick the hold
 * reaches its charge carries a player standing outside the pocket into their
 * room. Void's Outsider is {@code outsider charge=20}.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 *
 * @param charge the held ticks the channel asks before it carries the caster in
 */
public record OutsiderStep(Expr charge) implements Step {

    private static final String NAME = "outsider";
    private static final String FIELD_CHARGE = "charge";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<OutsiderStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHARGE).forGetter(OutsiderStep::charge)
    ).apply(inst, OutsiderStep::new));

    /**
     * The registered type.
     */
    public static final StepType<OutsiderStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<OutsiderStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        OutsiderHost host = context.hostAs(OutsiderHost.class);
        int held = host.channelAim().map(ChannelAim::held).orElse(0);
        if (!host.standsInPocket() && held == charge.evaluateInt(context)) {
            host.enterPocket();
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(charge);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.OUTSIDER);
    }
}

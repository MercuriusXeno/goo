package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether's Tartarus, run each held tick of the channel: each held tick
 * stands the gate the hold conjures before the player, as End's gate and
 * Zone's door stand, and the tick the hold reaches its charge carries them
 * through it onto the safe spot their hold's first tick found. A hold that
 * found none was refused before it paid, and stands nothing. Tartarus is
 * {@code tartarus charge=40 reach=4}.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 *
 * @param charge the held ticks the channel asks before it carries the caster
 * @param reach  how many blocks around the scaled spot's column the search looks, each way across X and Z
 */
public record TartarusStep(Expr charge, int reach) implements Step {

    private static final String NAME = "tartarus";
    private static final String FIELD_CHARGE = "charge";
    private static final String FIELD_REACH = "reach";

    /** Codec for the step's params. */
    public static final MapCodec<TartarusStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_CHARGE).forGetter(TartarusStep::charge),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_REACH).forGetter(TartarusStep::reach)
    ).apply(inst, TartarusStep::new));

    /** The registered type. */
    public static final StepType<TartarusStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The Tartarus step a program runs at its top level.
     *
     * @param program the ability's steps
     * @return the step, empty where the program runs none
     */
    public static Optional<TartarusStep> in(List<? extends Step> program) {
        return program.stream().filter(TartarusStep.class::isInstance).map(TartarusStep.class::cast).findFirst();
    }

    @Override
    public StepType<TartarusStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TartarusHost host = context.hostAs(TartarusHost.class);
        int held = host.channelAim().map(ChannelAim::held).orElse(0);
        int charged = charge.evaluateInt(context);
        if (held <= 0 || held > charged) {
            return true;
        }
        host.tartarusArrival().ifPresent(arrival -> {
            host.conjureTartarusGate(held, charged);
            if (held == charged) {
                host.carryThroughTartarusGate(arrival);
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
        return Set.of(HostCapability.TARTARUS);
    }
}

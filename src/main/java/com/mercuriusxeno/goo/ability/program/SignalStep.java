package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulse's Signal block step: pulse goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime. The cell it lays sends its network's
 * highest layer count as a redstone signal, and that is all it does, so the
 * step names nothing:
 * {@code signal}.
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 * pulse-goo-network-signals-at-its-highest-layer
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record SignalStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "signal";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<SignalStep> CODEC = MapCodec.unit(SignalStep::new);

    /**
     * The registered type.
     */
    public static final StepType<SignalStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SignalStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Signal goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.PULSE_SIGNAL.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.PULSE_SIGNAL.get());
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}

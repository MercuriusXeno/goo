package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock's Mortar block step: rock goo lands where each of the stream's drips
 * strikes, one layer for each drip, through the volume cell's one landing,
 * as Infest lays its nether goo and Vines its leaf goo: {@code mortar}.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * layer-of-rock-goo-is-a-stone-block
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record MortarStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "mortar";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<MortarStep> CODEC = MapCodec.unit(MortarStep::new);

    /**
     * The registered type.
     */
    public static final StepType<MortarStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<MortarStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Mortar goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.ROCK_MORTAR.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.ROCK_MORTAR.get());
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

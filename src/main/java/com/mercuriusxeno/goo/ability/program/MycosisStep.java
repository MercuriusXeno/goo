package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shroom's Mycosis block step: shroom goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Infest lays its nether goo. The program's entity steps, the
 * mycosis effect and the spores, run on a living thing a drip strikes and
 * on one touching the goo, and an infected thing's death bursts the goo
 * where it fell: {@code mycosis}.
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 * mycosis-spore-stream-buds-and-poisons
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record MycosisStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "mycosis";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<MycosisStep> CODEC = MapCodec.unit(MycosisStep::new);

    /**
     * The registered type.
     */
    public static final StepType<MycosisStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The mycosis step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the mycosis step, empty where the program lays no Mycosis goo
     */
    public static Optional<MycosisStep> in(List<Step> program) {
        return program.stream().filter(MycosisStep.class::isInstance).map(MycosisStep.class::cast).findFirst();
    }

    @Override
    public StepType<MycosisStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Mycosis goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.SHROOM_MYCOSIS.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.SHROOM_MYCOSIS.get());
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

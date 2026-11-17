package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly's Nectar block step: Jelly's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo. The step names the
 * guided throw: an aim passing a mob within the guide radius of the look
 * ray, inside the guide reach, throws the drips onto that mob:
 * {@code nectar_goo guide_radius=2 guide_reach=16}.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param guideRadius the blocks off the look ray a mob may lie and still take the guide
 * @param guideReach  the blocks along the look ray out to which a mob takes the guide
 */
public record NectarGooStep(double guideRadius, double guideReach) implements TakesNoEntity, DripLanding {

    private static final String NAME = "nectar_goo";
    private static final String FIELD_GUIDE_RADIUS = "guide_radius";
    private static final String FIELD_GUIDE_REACH = "guide_reach";
    /** The widest the guide reaches off the look ray. */
    private static final double MOST_GUIDE_RADIUS = 8;
    /** The farthest the guide reaches along the look ray. */
    private static final double MOST_GUIDE_REACH = 48;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NectarGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, MOST_GUIDE_RADIUS).fieldOf(FIELD_GUIDE_RADIUS).forGetter(NectarGooStep::guideRadius),
            Codec.doubleRange(0, MOST_GUIDE_REACH).fieldOf(FIELD_GUIDE_REACH).forGetter(NectarGooStep::guideReach)
    ).apply(inst, NectarGooStep::new));

    /**
     * The registered type.
     */
    public static final StepType<NectarGooStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The nectar goo step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the nectar goo step, empty where the program lays no Nectar goo
     */
    public static Optional<NectarGooStep> in(List<Step> program) {
        return program.stream().filter(NectarGooStep.class::isInstance).map(NectarGooStep.class::cast).findFirst();
    }

    @Override
    public StepType<NectarGooStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Nectar goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.JELLY_NECTAR.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.JELLY_NECTAR.get());
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

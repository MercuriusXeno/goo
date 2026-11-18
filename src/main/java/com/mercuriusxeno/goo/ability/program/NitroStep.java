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
 * Unstable's Nitro block step: unstable goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume
 * cell's one landing, as Infest lays its nether goo. The step names what a
 * Nitro cell explodes at: the power every cell blasts with, the power each
 * of its layers adds, and the ticks between one cell going off and the
 * next it sets off: {@code nitro base_power=1 power_per_layer=0.2 chain_ticks=4}.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 * lurker-blob-brightens-then-detonates
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param basePower     the power any Nitro cell blasts with, whatever its layers
 * @param powerPerLayer the power each layer of the cell adds to its blast
 * @param chainTicks    the ticks between a cell going off and the cells its blast reaches going off
 */
public record NitroStep(double basePower, double powerPerLayer, int chainTicks) implements TakesNoEntity, DripLanding {

    private static final String NAME = "nitro";
    private static final String FIELD_BASE_POWER = "base_power";
    private static final String FIELD_POWER_PER_LAYER = "power_per_layer";
    private static final String FIELD_CHAIN_TICKS = "chain_ticks";
    /** The strongest power a cell's base or a layer adds, a charged creeper's twice over. */
    private static final double MOST_POWER = 12.0;
    /** The longest beat between one cell and the next, a second. */
    private static final int MOST_CHAIN_TICKS = 20;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<NitroStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0.0, MOST_POWER).fieldOf(FIELD_BASE_POWER).forGetter(NitroStep::basePower),
            Codec.doubleRange(0.0, MOST_POWER).fieldOf(FIELD_POWER_PER_LAYER).forGetter(NitroStep::powerPerLayer),
            Codec.intRange(0, MOST_CHAIN_TICKS).fieldOf(FIELD_CHAIN_TICKS).forGetter(NitroStep::chainTicks)
    ).apply(inst, NitroStep::new));

    /**
     * The registered type.
     */
    public static final StepType<NitroStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The nitro step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the nitro step, empty where the program lays no Nitro goo
     */
    public static Optional<NitroStep> in(List<Step> program) {
        return program.stream().filter(NitroStep.class::isInstance).map(NitroStep.class::cast).findFirst();
    }

    /**
     * The power a Nitro cell of some layers blasts with: the base, and each layer's share on top, so the
     * more goo lies there the deadlier the blast.
     *
     * @param layers the cell's layers
     * @return the explosion's power
     */
    public float powerAt(int layers) {
        return (float) (basePower + powerPerLayer * layers);
    }

    @Override
    public StepType<NitroStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Nitro goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.UNSTABLE_NITRO.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.UNSTABLE_NITRO.get());
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

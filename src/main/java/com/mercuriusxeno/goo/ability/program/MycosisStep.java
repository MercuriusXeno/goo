package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
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
 * where it fell. The step names the reach a cell grows and tends fungus in,
 * the ticks between its growings, the layers each bud it grows spends, and
 * the layers each block of shroom network it spreads spends:
 * {@code mycosis reach=2 grow_ticks=20 layers_per_bud=1 layers_per_network=1}.
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 * mycosis-spore-stream-buds-and-poisons
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
 * mycosis-grows-and-reaps-nether-wart
 * colonize-blob-grows-the-network
 *
 * @param reach        the blocks around a cell, across and one up or down, it grows and tends fungus in
 * @param growTicks    the ticks between a cell's growings and tendings
 * @param layersPerBud the layers each bud a cell grows spends
 * @param layersPerNetwork the layers each block of shroom network a cell spreads spends
 */
public record MycosisStep(int reach, int growTicks, int layersPerBud, int layersPerNetwork)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "mycosis";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_GROW_TICKS = "grow_ticks";
    private static final String FIELD_LAYERS_PER_BUD = "layers_per_bud";
    private static final String FIELD_LAYERS_PER_NETWORK = "layers_per_network";
    /** The farthest a cell reaches, past which its scan of the blocks around it grows costly. */
    private static final int MOST_REACH = 8;
    /** The longest a cell waits between growings, a minute. */
    private static final int MOST_GROW_TICKS = 1200;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MycosisStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, MOST_REACH).fieldOf(FIELD_REACH).forGetter(MycosisStep::reach),
            Codec.intRange(1, MOST_GROW_TICKS).fieldOf(FIELD_GROW_TICKS).forGetter(MycosisStep::growTicks),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS_PER_BUD)
                    .forGetter(MycosisStep::layersPerBud),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS_PER_NETWORK)
                    .forGetter(MycosisStep::layersPerNetwork)
    ).apply(inst, MycosisStep::new));

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

    /**
     * The fungal bud a Mycosis cell grows, which the block layer reads through the step rather than the registry.
     * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
     *
     * @return the fungal bud at its first age
     */
    public BlockState budState() {
        return GooBlocks.FUNGAL_BUD.get().defaultBlockState();
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

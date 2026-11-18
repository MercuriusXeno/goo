package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shroom's Spores block step: shroom goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Infest lays its nether goo. The step names what a Spores cell
 * does on its period, a passive Mycosis: it emits its spore particle, and it
 * places one fungal bud on an edge space, spending a fixed count of layers:
 * {@code spores period=20 layers_per_bud=1 particle=goo:spore}.
 * spores-is-shrooms-residual-a-passive-mycosis
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param period       the ticks between a cell's spore emissions and buddings
 * @param layersPerBud the layers each bud a cell places spends
 * @param particle     the particle a cell emits on its period
 */
public record SporesStep(int period, int layersPerBud, Identifier particle) implements TakesNoEntity, DripLanding {

    private static final String NAME = "spores";
    private static final String FIELD_PERIOD = "period";
    private static final String FIELD_LAYERS_PER_BUD = "layers_per_bud";
    private static final String FIELD_PARTICLE = "particle";
    /** The longest a cell waits between periods, a minute. */
    private static final int MOST_PERIOD = 1200;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SporesStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, MOST_PERIOD).fieldOf(FIELD_PERIOD).forGetter(SporesStep::period),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS_PER_BUD)
                    .forGetter(SporesStep::layersPerBud),
            Identifier.CODEC.fieldOf(FIELD_PARTICLE).forGetter(SporesStep::particle)
    ).apply(inst, SporesStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SporesStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The particle a cell emits, and where.
     *
     * @param particle the particle's id
     * @param cell     the cell it rises from
     */
    public record SporeEmission(Identifier particle, BlockPos cell) {
    }

    /**
     * The spores step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the spores step, empty where the program lays no Spores goo
     */
    public static Optional<SporesStep> in(List<Step> program) {
        return program.stream().filter(SporesStep.class::isInstance).map(SporesStep.class::cast).findFirst();
    }

    /**
     * Whether a game time falls on the cell's period.
     *
     * @param gameTime the game time
     * @return true on each period's tick
     */
    public boolean onPeriod(long gameTime) {
        return gameTime % period == 0;
    }

    /**
     * The ticks from a game time to the next period's tick.
     *
     * @param gameTime the game time
     * @return the ticks to wait, a whole period on a period's own tick
     */
    public int ticksToNextPeriod(long gameTime) {
        return period - (int) (gameTime % period);
    }

    /**
     * What a cell emits at a game time: its spore particle on each period, nothing between.
     * spores-is-shrooms-residual-a-passive-mycosis
     *
     * @param gameTime the game time
     * @param cell     the cell
     * @return the emission, empty between periods
     */
    public Optional<SporeEmission> emissionAt(long gameTime, BlockPos cell) {
        return onPeriod(gameTime) ? Optional.of(new SporeEmission(particle, cell)) : Optional.empty();
    }

    /**
     * The fungal bud a Spores cell places, which the block layer reads through the step rather than the registry.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @return the fungal bud at its first age
     */
    public BlockState budState() {
        return GooBlocks.FUNGAL_BUD.get().defaultBlockState();
    }

    @Override
    public StepType<SporesStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Spores goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.SHROOM_SPORES.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.SHROOM_SPORES.get());
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

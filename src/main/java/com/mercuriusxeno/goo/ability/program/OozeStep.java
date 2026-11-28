package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Xeno's Ooze block step: xeno goo lands where each of the stream's drips
 * strikes, one layer for each drip, through the volume cell's one landing,
 * ordinary ground to stand on, and a slime standing on a cell holding the
 * turn's layers becomes a random hostile mob of its biome, the cell spending
 * them: {@code ooze slime_turn_layers=8 morph_ticks=20}, the steps under it
 * run on the mob born.
 * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param slimeTurnLayers the layers a slime's turn spends from the cell
 * @param morphTicks      the game ticks the goo's morph into the mob takes
 * @param steps           the steps run on the mob born, instant ones
 */
public record OozeStep(int slimeTurnLayers, int morphTicks, List<Step> steps) implements TakesNoEntity, DripLanding {

    private static final String NAME = "ooze";
    private static final String FIELD_SLIME_TURN_LAYERS = "slime_turn_layers";
    private static final String FIELD_MORPH_TICKS = "morph_ticks";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The list codec is read lazily because
     * {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<OozeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_SLIME_TURN_LAYERS)
                    .forGetter(OozeStep::slimeTurnLayers),
            Codec.INT.optionalFieldOf(FIELD_MORPH_TICKS, SlimeTransmuteStep.DEFAULT_MORPH_TICKS)
                    .forGetter(OozeStep::morphTicks),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STEPS, List.of())
                    .forGetter(OozeStep::steps)
    ).apply(inst, OozeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<OozeStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The ooze step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the ooze step, empty where the program lays no Ooze goo
     */
    public static Optional<OozeStep> in(List<Step> program) {
        return program.stream().filter(OozeStep.class::isInstance).map(OozeStep.class::cast).findFirst();
    }

    @Override
    public StepType<OozeStep> type() {
        return TYPE;
    }

    /**
     * The slime transmutation a turn on Ooze runs: xeno goo morphing into a
     * hostile mob, the step's sounds run on it.
     * ooze-is-xenos-world-goo-that-turns-slimes-into-random-hostiles
     *
     * @return the transmutation
     */
    public SlimeTransmuteStep slimeTurn() {
        return new SlimeTransmuteStep(GooTypes.XENO, true, morphTicks, steps);
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Ooze goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.XENO_OOZE.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.XENO_OOZE.get());
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
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

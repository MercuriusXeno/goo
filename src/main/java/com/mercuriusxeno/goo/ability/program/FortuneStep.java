package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
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
 * Ornate's Fortune block step: ornate goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, ordinary ground to stand on. A block broken with a Fortune cell
 * holding on one of its faces drops as if by Fortune, and a mob killed
 * standing in one drops as if by Looting, one level per
 * {@code layers_per_level} layers up to {@code max_level}:
 * {@code fortune layers_per_level=5 max_level=3}.
 * fortune-is-ornates-residual-that-makes-things-drop-more
 * ornate-is-the-fortune-goo-with-painting-and-coloring-abilities
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 *
 * @param layersPerLevel the layers each fortune level takes
 * @param maxLevel       the most levels a cell gives
 */
public record FortuneStep(int layersPerLevel, int maxLevel) implements TakesNoEntity, DripLanding {

    private static final String NAME = "fortune";
    private static final String FIELD_LAYERS_PER_LEVEL = "layers_per_level";
    private static final String FIELD_MAX_LEVEL = "max_level";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FortuneStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_LAYERS_PER_LEVEL)
                    .forGetter(FortuneStep::layersPerLevel),
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_MAX_LEVEL).forGetter(FortuneStep::maxLevel)
    ).apply(inst, FortuneStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FortuneStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The fortune step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the fortune step, empty where the program lays no Fortune goo
     */
    public static Optional<FortuneStep> in(List<Step> program) {
        return program.stream().filter(FortuneStep.class::isInstance).map(FortuneStep.class::cast).findFirst();
    }

    @Override
    public StepType<FortuneStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Fortune goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.ORNATE_FORTUNE.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.ORNATE_FORTUNE.get());
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

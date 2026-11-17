package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Typhoon's Updraft block step: Typhoon's goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Rime its frost goo. The step names
 * what an Updraft cell's column reads, its half width and the rise it
 * carries at in blocks per tick; the column stands a block over the cell's
 * top for each of its layers:
 * {@code updraft half_width=0.5 rise=0.4}.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * updraft-blob-stands-a-column-of-wind
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param halfWidth the column's half width in blocks, around the middle of the cell
 * @param rise      the rise the column carries at, in blocks per tick
 */
public record UpdraftStep(double halfWidth, double rise) implements TakesNoEntity, DripLanding {

    private static final String NAME = "updraft";
    private static final String FIELD_HALF_WIDTH = "half_width";
    private static final String FIELD_RISE = "rise";
    /** The widest a column stands: a block and a half each way from the cell's middle. */
    private static final double MOST_HALF_WIDTH = 1.5;
    /** The fastest a column carries: a block a tick. */
    private static final double MOST_RISE = 1.0;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<UpdraftStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, MOST_HALF_WIDTH).fieldOf(FIELD_HALF_WIDTH).forGetter(UpdraftStep::halfWidth),
            Codec.doubleRange(0, MOST_RISE).fieldOf(FIELD_RISE).forGetter(UpdraftStep::rise)
    ).apply(inst, UpdraftStep::new));

    /**
     * The registered type.
     */
    public static final StepType<UpdraftStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The updraft step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the updraft step, empty where the program lays no Updraft goo
     */
    public static Optional<UpdraftStep> in(List<Step> program) {
        return program.stream().filter(UpdraftStep.class::isInstance).map(UpdraftStep.class::cast).findFirst();
    }

    /**
     * How far an Updraft cell's column stands over the cell's top: a block
     * for each of its layers, so fifteen layers lift fifteen blocks.
     * updraft-goo-lifts-one-block-per-layer
     *
     * @param layers the cell's layers
     * @return the column's height over the cell's top, in blocks
     */
    public static double heightOver(int layers) {
        return layers;
    }

    /**
     * Lifts every entity whose feet stand in an Updraft cell's column: from
     * the cell's floor, through its goo, up to {@link #heightOver} blocks
     * over its top, as wide as the step names, at the step's rise. Lifting
     * spends none of the cell's layers.
     * updraft-goo-is-semi-permanent-and-does-not-deplete
     *
     * @param level  the server level
     * @param cell   the Updraft cell
     * @param layers the cell's layers
     */
    public void liftAbove(ServerLevel level, BlockPos cell, int layers) {
        double goo = (double) layers / VolumeCellBlock.MAX_LAYERS;
        EntityLift.liftInColumn(level, Vec3.atBottomCenterOf(cell), halfWidth, goo + heightOver(layers), rise);
    }

    @Override
    public StepType<UpdraftStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Updraft goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<BlockPos> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.TYPHOON_UPDRAFT.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.TYPHOON_UPDRAFT.get());
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

package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deep's Host block step: Deep's sculky slime lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Molten its molten goo. The step
 * names the layers a Host cell spends turning the block under it to sculk
 * when a sculk charge crosses it, and how near Host goo a mob's death must
 * fall for the goo to take its charge, starting at a sculk catalyst's own
 * reach: {@code host sculk_layers=3 radius=8}.
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * sculk-charge-runs-along-host-goo-without-weakening
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param sculkLayers the layers a Host cell spends turning the block under it to sculk
 * @param radius      how far from a death a Host cell still takes its charge, in blocks
 */
public record HostStep(int sculkLayers, int radius) implements TakesNoEntity, DripLanding {

    private static final String NAME = "host";
    private static final String FIELD_SCULK_LAYERS = "sculk_layers";
    private static final String FIELD_RADIUS = "radius";
    /** The farthest a death may fall from Host goo, bounding the blocks a death searches. */
    private static final int MOST_RADIUS = 32;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HostStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_SCULK_LAYERS).forGetter(HostStep::sculkLayers),
            Codec.intRange(1, MOST_RADIUS).fieldOf(FIELD_RADIUS).forGetter(HostStep::radius)
    ).apply(inst, HostStep::new));

    /**
     * The registered type.
     */
    public static final StepType<HostStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The host step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the host step, empty where the program lays no Host goo
     */
    public static Optional<HostStep> in(List<Step> program) {
        return program.stream().filter(HostStep.class::isInstance).map(HostStep.class::cast).findFirst();
    }

    @Override
    public StepType<HostStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Host goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.DEEP_HOST.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.DEEP_HOST.get());
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

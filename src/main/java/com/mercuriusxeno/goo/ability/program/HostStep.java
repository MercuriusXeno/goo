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
 * when a sculk charge crosses it: {@code host sculk_layers=3}.
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * sculk-charge-runs-along-host-goo-without-weakening
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param sculkLayers the layers a Host cell spends turning the block under it to sculk
 */
public record HostStep(int sculkLayers) implements TakesNoEntity, DripLanding {

    private static final String NAME = "host";
    private static final String FIELD_SCULK_LAYERS = "sculk_layers";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<HostStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, VolumeCellBlock.MAX_LAYERS).fieldOf(FIELD_SCULK_LAYERS).forGetter(HostStep::sculkLayers)
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

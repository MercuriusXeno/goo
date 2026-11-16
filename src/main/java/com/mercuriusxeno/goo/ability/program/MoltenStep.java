package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Blaze's Molten block step: Blaze's molten goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Slick its slick; a drip striking a
 * lava source lays its layer on the lava's surface, where the goo pools. The
 * tag names the stone the goo stands over to melt:
 * {@code molten tag=goo:meltable heat_ticks=40}. A cell melting a block
 * heats it red hot for the heat's ticks before it becomes lava.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * stone-goes-red-hot-before-molten-turns-it-to-lava
 * melt-blob-absorbs-into-lava
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param meltable  the blocks that melt
 * @param heatTicks the ticks a melting block stands red hot before it becomes lava
 */
public record MoltenStep(TagKey<Block> meltable, int heatTicks) implements TakesNoEntity, DripLanding {

    private static final String NAME = "molten";
    private static final String FIELD_TAG = "tag";
    private static final String FIELD_HEAT_TICKS = "heat_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MoltenStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_TAG).forGetter(MoltenStep::meltable),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_HEAT_TICKS).forGetter(MoltenStep::heatTicks)
    ).apply(inst, MoltenStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MoltenStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The molten step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the molten step, empty where the program lays no molten goo
     */
    public static Optional<MoltenStep> in(List<Step> program) {
        return program.stream().filter(MoltenStep.class::isInstance).map(MoltenStep.class::cast).findFirst();
    }

    @Override
    public StepType<MoltenStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the molten goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.BLAZE_MOLTEN.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.BLAZE_MOLTEN.get());
    }

    /**
     * Molten's drips strike a lava source at its surface, where the goo pools.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     */
    @Override
    public boolean landsOnLava() {
        return CellBinding.MOLTEN.liesOnLava();
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

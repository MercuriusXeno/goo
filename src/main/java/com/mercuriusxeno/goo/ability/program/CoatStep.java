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
 * Durable's Coat block step: durable goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing. Runny and hanging, it spreads thin over tops, sides and
 * undersides and wraps what it is sprayed on; its cell resists blasts and
 * picks as obsidian does and keeps the block it stands against. A damaged
 * item lying in it is repaired each period the step names, the cell
 * spending a layer: {@code coat repair_ticks=20}.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 * items-dropped-in-durable-goo-are-repaired-by-their-worth
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 *
 * @param repairTicks the ticks between the repairs an item lying in the cell takes
 */
public record CoatStep(int repairTicks) implements TakesNoEntity, DripLanding {

    private static final String NAME = "coat";
    private static final String FIELD_REPAIR_TICKS = "repair_ticks";
    /** The longest period between repairs, ten minutes. */
    private static final int MOST_REPAIR_TICKS = 12_000;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CoatStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, MOST_REPAIR_TICKS).fieldOf(FIELD_REPAIR_TICKS).forGetter(CoatStep::repairTicks)
    ).apply(inst, CoatStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CoatStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The coat step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the coat step, empty where the program lays no Coat
     */
    public static Optional<CoatStep> in(List<Step> program) {
        return program.stream().filter(CoatStep.class::isInstance).map(CoatStep.class::cast).findFirst();
    }

    @Override
    public StepType<CoatStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Coat goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.DURABLE_COAT.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.DURABLE_COAT.get());
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

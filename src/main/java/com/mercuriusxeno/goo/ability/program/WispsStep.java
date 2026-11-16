package com.mercuriusxeno.goo.ability.program;

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
 * Glow's Wisps block step: glow's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Host its sculky slime, and the cell
 * shines at its layers. The step names the holy damage a Wisps cell deals
 * an undead touching it for each layer it holds, and the ticks between two
 * of those hits: {@code wisps holy_per_layer=0.5 holy_every=20}.
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 * wisps-goo-glows-at-a-light-level-equal-to-its-layers
 * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param holyPerLayer the holy damage a Wisps cell deals an undead touching it, for each layer it holds
 * @param holyEvery    the ticks between two holy hits on an undead touching the goo
 */
public record WispsStep(double holyPerLayer, int holyEvery) implements TakesNoEntity, DripLanding {

    private static final String NAME = "wisps";
    private static final String FIELD_HOLY_PER_LAYER = "holy_per_layer";
    private static final String FIELD_HOLY_EVERY = "holy_every";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<WispsStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_HOLY_PER_LAYER).forGetter(WispsStep::holyPerLayer),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_HOLY_EVERY).forGetter(WispsStep::holyEvery)
    ).apply(inst, WispsStep::new));

    /**
     * The registered type.
     */
    public static final StepType<WispsStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The wisps step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the wisps step, empty where the program lays no Wisps goo
     */
    public static Optional<WispsStep> in(List<Step> program) {
        return program.stream().filter(WispsStep.class::isInstance).map(WispsStep.class::cast).findFirst();
    }

    @Override
    public StepType<WispsStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Wisps goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.GLOW_WISPS.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.GLOW_WISPS.get());
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

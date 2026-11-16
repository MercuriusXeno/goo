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
 * Frost's Rime block step: Frost's endothermic goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Host its sculky slime. The step
 * names how far around a Rime cell the water and lava stand frozen, the
 * ticks between a cell's passes freezing them, the frozen ticks a barefoot
 * living thing standing on Rime gains each tick, and the freeze damage it
 * takes every so many ticks:
 * {@code rime reach=3 chill_ticks=20 freeze_ticks=4 damage=1 damage_ticks=20}.
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param reach       how far from a Rime cell water and lava stand frozen, in blocks along each axis
 * @param chillTicks  the ticks between a Rime cell's passes freezing what is in its reach
 * @param freezeTicks the frozen ticks a barefoot living thing standing on Rime gains each tick
 * @param damage      the freeze damage a barefoot living thing standing on Rime takes on each cadence
 * @param damageTicks the ticks between the freeze damage a barefoot living thing standing on Rime takes
 */
public record RimeStep(int reach, int chillTicks, int freezeTicks, float damage, int damageTicks)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "rime";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_CHILL_TICKS = "chill_ticks";
    private static final String FIELD_FREEZE_TICKS = "freeze_ticks";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_DAMAGE_TICKS = "damage_ticks";
    /** The most frozen ticks a step on Rime adds: powder snow's whole freeze at once. */
    private static final int MOST_FREEZE_TICKS = 140;
    /** The most freeze damage one cadence deals: a whole player's health. */
    private static final float MOST_DAMAGE = 20.0F;
    /** The farthest a Rime cell's reach runs, bounding the blocks each pass and each thaw check reads. */
    private static final int MOST_REACH = 8;
    /** The longest wait between a Rime cell's passes: a minute. */
    private static final int MOST_CHILL_TICKS = 1200;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<RimeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, MOST_REACH).fieldOf(FIELD_REACH).forGetter(RimeStep::reach),
            Codec.intRange(1, MOST_CHILL_TICKS).fieldOf(FIELD_CHILL_TICKS).forGetter(RimeStep::chillTicks),
            Codec.intRange(0, MOST_FREEZE_TICKS).fieldOf(FIELD_FREEZE_TICKS).forGetter(RimeStep::freezeTicks),
            Codec.floatRange(0, MOST_DAMAGE).fieldOf(FIELD_DAMAGE).forGetter(RimeStep::damage),
            Codec.intRange(1, MOST_CHILL_TICKS).fieldOf(FIELD_DAMAGE_TICKS).forGetter(RimeStep::damageTicks)
    ).apply(inst, RimeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RimeStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The rime step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the rime step, empty where the program lays no Rime goo
     */
    public static Optional<RimeStep> in(List<Step> program) {
        return program.stream().filter(RimeStep.class::isInstance).map(RimeStep.class::cast).findFirst();
    }

    @Override
    public StepType<RimeStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Rime goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.FROST_RIME.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.FROST_RIME.get());
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

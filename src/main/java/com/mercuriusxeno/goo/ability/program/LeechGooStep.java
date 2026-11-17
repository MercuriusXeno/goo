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
 * Hex's Leech block step: Hex's goo lands where each of the stream's drips
 * strikes, one layer for each drip, through the volume cell's one landing, as
 * Goop lays its slime and Host its sculky slime. The step names the share of
 * its speed a thing caught in Leech goo keeps, as soul sand's speed factor
 * reads, the share of a strike's damage on a thing in the goo that drains
 * to the striker, the chance a strike that drains life takes a layer from
 * the cell under its victim, the chance a living thing standing in the goo
 * pops a soul off its body each client tick, and the ticks between the hex
 * wisps it sheds: {@code leech_goo speed_factor=0.5 drain_fraction=0.3
 * deplete_chance=0.1 soul_pop_chance=0.01 wisp_shed_ticks=10}.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 * soul-particles-pop-from-leech-goo-now-and-then
 * hex-wisps-drift-off-mobs-standing-in-leech-goo
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param speedFactor   the share of its horizontal speed a thing caught in Leech goo keeps
 * @param drainFraction the share of a strike's damage on a thing in the goo that heals the striker
 * @param depleteChance the chance a drained strike takes a layer from the cell under its victim
 * @param soulPopChance the chance a living thing standing in the goo pops a soul each client tick
 * @param wispShedTicks the ticks between the hex wisps a living thing standing in the goo sheds
 */
public record LeechGooStep(double speedFactor, float drainFraction, double depleteChance, double soulPopChance,
                           int wispShedTicks) implements TakesNoEntity, DripLanding {

    private static final String NAME = "leech_goo";
    private static final String FIELD_SPEED_FACTOR = "speed_factor";
    private static final String FIELD_DRAIN_FRACTION = "drain_fraction";
    private static final String FIELD_DEPLETE_CHANCE = "deplete_chance";
    private static final String FIELD_SOUL_POP_CHANCE = "soul_pop_chance";
    private static final String FIELD_WISP_SHED_TICKS = "wisp_shed_ticks";
    /** The longest a standing thing waits between shed wisps, a minute. */
    private static final int MOST_WISP_SHED_TICKS = 1200;
    /** The least share of its speed a caught thing keeps, so the goo slows and never pins. */
    private static final double LEAST_SPEED_FACTOR = 0.01;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LeechGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(LEAST_SPEED_FACTOR, 1.0).fieldOf(FIELD_SPEED_FACTOR)
                    .forGetter(LeechGooStep::speedFactor),
            Codec.floatRange(0f, 1f).fieldOf(FIELD_DRAIN_FRACTION).forGetter(LeechGooStep::drainFraction),
            Codec.doubleRange(0.0, 1.0).fieldOf(FIELD_DEPLETE_CHANCE).forGetter(LeechGooStep::depleteChance),
            Codec.doubleRange(0.0, 1.0).fieldOf(FIELD_SOUL_POP_CHANCE).forGetter(LeechGooStep::soulPopChance),
            Codec.intRange(1, MOST_WISP_SHED_TICKS).fieldOf(FIELD_WISP_SHED_TICKS)
                    .forGetter(LeechGooStep::wispShedTicks)
    ).apply(inst, LeechGooStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LeechGooStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The leech goo step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the leech goo step, empty where the program lays no Leech goo
     */
    public static Optional<LeechGooStep> in(List<Step> program) {
        return program.stream().filter(LeechGooStep.class::isInstance).map(LeechGooStep.class::cast).findFirst();
    }

    @Override
    public StepType<LeechGooStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Leech goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.HEX_LEECH.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.HEX_LEECH.get());
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

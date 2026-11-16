package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Hex's Leech block step: Hex's goo lands where each of the stream's drips
 * strikes, one layer for each drip, through the volume cell's one landing, as
 * Goop lays its slime and Host its sculky slime. The step names the share of
 * its speed a thing caught in Leech goo keeps, as soul sand's speed factor
 * reads, the damage the goo's spirits deal a living thing caught in it, the
 * ticks between their bites, and the chance each bite takes a layer from the
 * cell: {@code leech_goo speed_factor=0.4 spirit_damage=1 spirit_ticks=20
 * deplete_chance=0.1}.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param speedFactor   the share of its horizontal speed a thing caught in Leech goo keeps
 * @param spiritDamage  the damage the spirits deal a living thing caught in the goo at each bite
 * @param spiritTicks   the ticks between the spirits' bites
 * @param depleteChance the chance each bite takes a layer from the cell under the bitten thing
 */
public record LeechGooStep(double speedFactor, float spiritDamage, int spiritTicks, double depleteChance)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "leech_goo";
    private static final String FIELD_SPEED_FACTOR = "speed_factor";
    private static final String FIELD_SPIRIT_DAMAGE = "spirit_damage";
    private static final String FIELD_SPIRIT_TICKS = "spirit_ticks";
    private static final String FIELD_DEPLETE_CHANCE = "deplete_chance";
    /** The most damage one bite deals, a whole health bar of a player at a time. */
    private static final float MOST_SPIRIT_DAMAGE = 20f;
    /** The longest the spirits wait between bites, a minute. */
    private static final int MOST_SPIRIT_TICKS = 1200;
    /** The least share of its speed a caught thing keeps, so the goo slows and never pins. */
    private static final double LEAST_SPEED_FACTOR = 0.01;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LeechGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(LEAST_SPEED_FACTOR, 1.0).fieldOf(FIELD_SPEED_FACTOR)
                    .forGetter(LeechGooStep::speedFactor),
            Codec.floatRange(0f, MOST_SPIRIT_DAMAGE).fieldOf(FIELD_SPIRIT_DAMAGE)
                    .forGetter(LeechGooStep::spiritDamage),
            Codec.intRange(1, MOST_SPIRIT_TICKS).fieldOf(FIELD_SPIRIT_TICKS).forGetter(LeechGooStep::spiritTicks),
            Codec.doubleRange(0.0, 1.0).fieldOf(FIELD_DEPLETE_CHANCE).forGetter(LeechGooStep::depleteChance)
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

    /**
     * The multiplier a thing caught in Leech goo moves under: its horizontal
     * speed by the speed factor, its rise and fall untouched.
     *
     * @return the movement multiplier
     */
    public Vec3 slowing() {
        return new Vec3(speedFactor, 1.0, speedFactor);
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

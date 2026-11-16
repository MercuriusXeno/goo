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
 * reads: {@code leech_goo speed_factor=0.4}.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param speedFactor the share of its horizontal speed a thing caught in Leech goo keeps
 */
public record LeechGooStep(double speedFactor) implements TakesNoEntity, DripLanding {

    private static final String NAME = "leech_goo";
    private static final String FIELD_SPEED_FACTOR = "speed_factor";
    /** The least share of its speed a caught thing keeps, so the goo slows and never pins. */
    private static final double LEAST_SPEED_FACTOR = 0.01;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LeechGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(LEAST_SPEED_FACTOR, 1.0).fieldOf(FIELD_SPEED_FACTOR)
                    .forGetter(LeechGooStep::speedFactor)
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

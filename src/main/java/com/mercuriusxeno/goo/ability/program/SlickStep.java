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
 * Kinetic Slick's block step: Kinetic's slick lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime, whatever the caster's speed; a drip
 * still in flight when the hold ends lands its layer when it strikes. The
 * slick it lays reads its ramp and top speed from this step, so the JSON
 * names how fast the slick drives a thing trying to move on it: the ramp
 * starts gentle and grows to its strongest over the build's ticks the thing
 * keeps trying, and a thing stepping on slower than the start speed is
 * raised to it, at any height of the cell.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * slick-coats-the-ground-with-kinetic-speed
 * slick-drains-per-film-it-lays
 * slick-ramp-grows-while-a-thing-keeps-moving
 * slick-raises-a-slow-entry-to-a-start-speed
 * slick-resolves-faster-by-speed-over-unadjusted-speed
 *
 * @param startRamp     the factor the slick multiplies a trying thing's horizontal velocity by on its first tick
 * @param strongestRamp the factor the ramp grows to
 * @param buildTicks    the ticks of trying the ramp takes to grow from its start to its strongest
 * @param topSpeed      the horizontal speed the ramp holds a thing to, in blocks a tick
 * @param startSpeed    the horizontal speed a trying thing stepping on slower is raised to, in blocks a tick
 */
public record SlickStep(double startRamp, double strongestRamp, int buildTicks, double topSpeed,
                        double startSpeed) implements TakesNoEntity, DripLanding {

    private static final String NAME = "slick";
    private static final String FIELD_START_RAMP = "start_ramp";
    private static final String FIELD_STRONGEST_RAMP = "strongest_ramp";
    private static final String FIELD_BUILD_TICKS = "build_ticks";
    private static final String FIELD_TOP_SPEED = "top_speed";
    private static final String FIELD_START_SPEED = "start_speed";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlickStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(1, Double.MAX_VALUE).fieldOf(FIELD_START_RAMP).forGetter(SlickStep::startRamp),
            Codec.doubleRange(1, Double.MAX_VALUE).fieldOf(FIELD_STRONGEST_RAMP).forGetter(SlickStep::strongestRamp),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_BUILD_TICKS).forGetter(SlickStep::buildTicks),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_TOP_SPEED).forGetter(SlickStep::topSpeed),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_START_SPEED).forGetter(SlickStep::startSpeed)
    ).apply(inst, SlickStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SlickStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The slick step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the slick step, empty where the program lays no slick
     */
    public static Optional<SlickStep> in(List<Step> program) {
        return program.stream().filter(SlickStep.class::isInstance).map(SlickStep.class::cast).findFirst();
    }

    @Override
    public StepType<SlickStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the slick as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.KINETIC_SLICK.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.KINETIC_SLICK.get());
    }

    /**
     * Slick throws its drips faster by its caster's speed over their walk.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     */
    @Override
    public boolean throwsFasterBySpeed() {
        return true;
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

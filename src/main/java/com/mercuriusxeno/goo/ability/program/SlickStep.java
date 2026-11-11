package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Kinetic Slick's block step, run each held tick of the stream: the stream
 * paints the open cell over every block it reaches whose top can hold a
 * coat, and every cell the hold has painted builds toward a slick layer while
 * the hold lasts, laying it once it has built the JSON's ticks, as Goop lays
 * its slime. The slick it lays reads its ramp and top speed from this step,
 * so the JSON names how fast the slick drives a thing trying to move on it:
 * the ramp starts gentle and grows to its strongest over the build's ticks
 * the thing keeps trying, and a thing stepping on slower than the start
 * speed is raised to it.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
 * slick-raises-a-slow-entry-to-a-start-speed
 *
 * @param ticks         the ticks of stream a cell takes to slick
 * @param startRamp     the factor the slick multiplies a trying thing's horizontal velocity by on its first tick
 * @param strongestRamp the factor the ramp grows to
 * @param buildTicks    the ticks of trying the ramp takes to grow from its start to its strongest
 * @param topSpeed      the horizontal speed the ramp holds a thing to, in blocks a tick
 * @param startSpeed    the horizontal speed a trying thing stepping on slower is raised to, in blocks a tick
 */
public record SlickStep(int ticks, double startRamp, double strongestRamp, int buildTicks, double topSpeed,
                        double startSpeed) implements TakesNoEntity {

    private static final String NAME = "slick";
    private static final String FIELD_TICKS = "ticks";
    private static final String FIELD_START_RAMP = "start_ramp";
    private static final String FIELD_STRONGEST_RAMP = "strongest_ramp";
    private static final String FIELD_BUILD_TICKS = "build_ticks";
    private static final String FIELD_TOP_SPEED = "top_speed";
    private static final String FIELD_START_SPEED = "start_speed";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SlickStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_TICKS).forGetter(SlickStep::ticks),
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

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                paintOpenTops(host, PaintedHold.seenReach(host, aim), marks);
            }
            PaintedHold.buildPaintedStates(host, SlickStep::slickOver, SlickStep::sameBlock, marks, 1f / ticks,
                    raw -> OptionalInt.empty(), (pos, raw, into) -> host.transformBlock(pos, into));
        });
        return true;
    }

    /**
     * Paints the open cell over each reached block whose top can hold a coat.
     *
     * @param host  the channel host
     * @param seen  the cells the stream's rays reached
     * @param marks the hold's marks
     */
    private static void paintOpenTops(ChannelHost host, Collection<BlockPos> seen, HoldMarks marks) {
        for (BlockPos pos : seen) {
            BlockPos over = pos.above();
            if (!host.airAt(pos) && host.airAt(over) && SlimeCoatBlock.liesOn(host.level(), over)) {
                marks.paint(over, host.stateAt(over));
            }
        }
    }

    /**
     * What a painted cell becomes: an open cell takes the slick, anything else nothing.
     *
     * @param standing the state the cell held when painted
     * @return the slick, empty where the cell takes none
     */
    private static Optional<BlockState> slickOver(BlockState standing) {
        return standing.isAir() ? Optional.of(GooBlocks.KINETIC_SLICK.get().defaultBlockState()) : Optional.empty();
    }

    /**
     * Whether a painted cell still stands as painted.
     *
     * @param now the cell's state now
     * @param raw the state it held when painted
     * @return true while the hold builds on it
     */
    private static boolean sameBlock(BlockState now, BlockState raw) {
        return now.getBlock() == raw.getBlock();
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

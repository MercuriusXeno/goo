package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlickLayPace;
import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Kinetic Slick's block step: the film lands where each of the stream's
 * drips strikes. A drip striking a block's top feeds the open cell over it,
 * where that top can hold a coat, and a drip striking a side feeds nothing.
 * Each strike builds its cell one share of the JSON's ticks toward a film,
 * shortened by the channeler's speed on slick, and a cell past nine tenths of
 * its film finishes alone; a drip still in flight when the hold ends lands
 * its share when it strikes, as Goop lays its slime. The slick it lays reads its ramp and top speed from this step,
 * so the JSON names how fast the slick drives a thing trying to move on it:
 * the ramp starts gentle and grows to its strongest over the build's ticks
 * the thing keeps trying, and a thing stepping on slower than the start
 * speed is raised to it. Each film pays Slick's cost as it lands.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * slick-coats-the-ground-with-kinetic-speed
 * slick-drains-per-film-it-lays
 * slick-ramp-grows-while-a-thing-keeps-moving
 * slick-raises-a-slow-entry-to-a-start-speed
 *
 * @param ticks         the ticks of strikes a cell takes to slick
 * @param startRamp     the factor the slick multiplies a trying thing's horizontal velocity by on its first tick
 * @param strongestRamp the factor the ramp grows to
 * @param buildTicks    the ticks of trying the ramp takes to grow from its start to its strongest
 * @param topSpeed      the horizontal speed the ramp holds a thing to, in blocks a tick
 * @param startSpeed    the horizontal speed a trying thing stepping on slower is raised to, in blocks a tick
 */
public record SlickStep(int ticks, double startRamp, double strongestRamp, int buildTicks, double topSpeed,
                        double startSpeed) implements TakesNoEntity, DripLanding {

    private static final String NAME = "slick";
    private static final String FIELD_TICKS = "ticks";
    private static final String FIELD_START_RAMP = "start_ramp";
    private static final String FIELD_STRONGEST_RAMP = "strongest_ramp";
    private static final String FIELD_BUILD_TICKS = "build_ticks";
    private static final String FIELD_TOP_SPEED = "top_speed";
    private static final String FIELD_START_SPEED = "start_speed";
    /** One whole cost buys one film (decision slick-drains-per-film-it-lays). */
    private static final int FILMS_PER_COST = 1;

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

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the film as they strike.
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
        return cellFedBy(strike, pos -> host.airAt(pos) && SlimeCoatBlock.liesOn(host.level(), pos));
    }

    /**
     * The cell a drip's strike feeds: the open cell over a struck top that
     * can hold a coat; a strike on a block's side or bottom feeds nothing.
     * This is the one seam turning a strike into a film's cell.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param strike      where the drip struck
     * @param takesAFilm  whether a cell stands open over a top that can hold a coat
     * @return the cell, empty where the strike lays nothing
     */
    static Optional<BlockPos> cellFedBy(CoatRay.BlockStrike strike, Predicate<BlockPos> takesAFilm) {
        // the operator's ruling: a coat lies only on a block's top, so a struck side lays nothing and slides nowhere
        BlockPos over = strike.block().above();
        return strike.face() == Direction.UP && takesAFilm.test(over) ? Optional.of(over) : Optional.empty();
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        if (host.finishingAlone(cell) || !host.airAt(cell)) {
            return;
        }
        BlockState slick = GooBlocks.KINETIC_SLICK.get().defaultBlockState();
        PlacementPrice price = host.placementPrice();
        float share = layRate(host);
        float built = host.exposeBlock(cell, slick, share);
        if (built >= 1f) {
            layPaidFor(host, price, cell, slick);
        } else if (built > PaintedHold.FINISHES_ALONE) {
            host.finishBlockAlone(cell, share, () -> layPaidFor(host, price, cell, slick));
        }
    }

    /**
     * Lands a film once the player pays its price, Slick's whole cost, the
     * hold's or a film finishing alone after release alike; a film the
     * player cannot pay for does not land.
     * slick-drains-per-film-it-lays
     *
     * @param host  the channel host
     * @param price what the film pays as it lands
     * @param pos   the cell
     * @param into  the slick
     */
    private static void layPaidFor(ChannelHost host, PlacementPrice price, BlockPos pos, BlockState into) {
        if (price.pay(FILMS_PER_COST)) {
            host.transformBlock(pos, into);
        }
    }

    /**
     * The share of a cell one strike builds: the JSON's ticks
     * shortened by the channeler's speed while they stand on slick, so slick
     * lands in time to step on.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @param host the channel host
     * @return the share
     */
    float layRate(ChannelHost host) {
        double factor = SlickLayPace.factor(host.channelerTravelSpeed(), host.channelerUnadjustedSpeed(),
                host.channelerStandsOnSlick());
        return (float) (factor / ticks);
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

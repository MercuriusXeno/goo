package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rock bore's step, run each held tick of the stream: it drills the
 * breakable blocks at its point, where the eye line meets the first
 * breakable block within the stream's reach, whatever the stream's cone:
 * that block and every breakable block within the JSON's point radius of the
 * point, never wider than a block, never every block the drill touches
 * ({@link BorePick}). Each block picked progresses toward broken each tick
 * through the hold's gradual break at the pace its JSON names, and the block
 * behind each at half speed, its JSON slowdown of 2, out of view; once the
 * pick breaks the point lands on the block behind, already cracking, so the
 * tunnel goes one block deep at a time and moves smoothly. A solid block outside
 * the tag on the eye line stops the bore there. Each tick the strike steps
 * run on every living entity whose body overlaps the cells the eye line
 * crosses out to the point and that every where filter keeps, so a mob in
 * the tunnel takes Bore's damage and one behind the stopping block takes
 * none (decision bore-vortex-with-a-worldspace-shake).
 * decision bore-breaks-the-block-at-the-point-and-its-surroundings
 * decision bore-block-behind-progresses-at-half-speed
 * decision bore-breaks-what-is-in-the-vortex
 * decision bore-drills-the-eye-line-with-steady-progress
 * decision bore-and-flatten-json-carry-two-pace-values
 *
 * @param breaks      the block tag naming the blocks bore may break
 * @param pace        how fast the picked blocks progress toward broken, and how many times slower the blocks behind
 * @param pointRadius how far round the point the pick reaches, in blocks; 0 picks the aimed block alone
 * @param where       the filters an entity in the tunnel must pass to be struck
 * @param strike      the steps run on each struck entity; empty strikes nothing
 */
public record BoreStep(TagKey<Block> breaks, BreakPace pace, double pointRadius,
                       List<EntityFilter> where, List<Step> strike) implements Step {

    private static final String NAME = "bore";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STRIKE = "strike";
    private static final String FIELD_POINT_RADIUS = "point_radius";
    /** The widest the point's radius runs: half a block, so the point is never wider than a block. */
    private static final double WIDEST_RADIUS = 0.5;
    /** The distance between samples along the look, fine enough to visit every block it crosses. */
    private static final double SAMPLE_STEP = 0.05;

    /**
     * Codec for the step's params. The strike list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<BoreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(BoreStep::breaks),
            BreakPace.CODEC.forGetter(BoreStep::pace),
            Codec.doubleRange(0, WIDEST_RADIUS).optionalFieldOf(FIELD_POINT_RADIUS, 0.0)
                    .forGetter(BoreStep::pointRadius),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(BoreStep::where),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).optionalFieldOf(FIELD_STRIKE, List.of())
                    .forGetter(BoreStep::strike)
    ).apply(inst, BoreStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BoreStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A bore that strikes nothing in its tunnel.
     *
     * @param breaks the block tag naming the blocks bore may break
     * @param pace   how fast the picked blocks progress toward broken
     */
    public BoreStep(TagKey<Block> breaks, BreakPace pace) {
        this(breaks, pace, 0, List.of(), List.of());
    }

    /**
     * A bore picking the aimed block alone, striking what its where filters keep.
     *
     * @param breaks the block tag naming the blocks bore may break
     * @param pace   how fast the picked blocks progress toward broken
     * @param where  the filters an entity in the tunnel must pass to be struck
     * @param strike the steps run on each struck entity
     */
    public BoreStep(TagKey<Block> breaks, BreakPace pace, List<EntityFilter> where, List<Step> strike) {
        this(breaks, pace, 0, where, strike);
    }

    @Override
    public StepType<BoreStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        Predicate<BlockPos> breakable = pos -> host.blockIn(pos, breaks);
        host.channelAim().ifPresent(aim -> {
            BorePick pick = BorePick.of(host.eye(), pointRadius, aim, host::airAt, breakable);
            host.gradualBreak().progress(pace.ratesByGroup(List.of(pick.blocks(), pick.behind())), host, true);
            strikeIn(host, pick.tunnel());
        });
        return true;
    }

    /**
     * The blocks the bore is progressing while the aim holds: the pick its
     * tick hands the gradual break, so a client shakes exactly the blocks the
     * server is cracking.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     * decision bore-breaks-the-block-at-the-point-and-its-surroundings
     *
     * @param eye         the eye the line runs from
     * @param pointRadius how far round the point the pick reaches; 0 picks the aimed block alone
     * @param aim         the stream's aim: its reach along the look
     * @param air         whether a cell holds air
     * @param breakable   whether a cell holds a block in the tag
     * @return the blocks at the point, the aimed block first
     */
    public static List<BlockPos> progressing(Vec3 eye, double pointRadius, ChannelAim aim,
                                             Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        return BorePick.of(eye, pointRadius, aim, air, breakable).blocks();
    }

    /**
     * Where the bore's point stands along an eye line: the point its pick
     * breaks at, which the drawn drill narrows to.
     * decision bore-drill-point-floats-along-the-crosshair-to-the-aimed-block
     *
     * @param eye       the eye the line runs from
     * @param reachEnd  the end of the reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the point, empty where the line meets no breakable block within the reach
     */
    public static Optional<BorePoint> pointOf(Vec3 eye, Vec3 reachEnd, Predicate<BlockPos> air,
                                              Predicate<BlockPos> breakable) {
        return BorePick.pointOf(eye, reachEnd, air, breakable);
    }

    /**
     * Where the bore's eye line stops: at its point on a breakable block, at
     * the first solid block outside the tag, or at the end of the reach, so a
     * drill drawn to it never runs into a block it does not break.
     *
     * @param eye       the eye the line runs from
     * @param reachEnd  the end of the reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return where the line stops
     */
    public static Vec3 stopOf(Vec3 eye, Vec3 reachEnd, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        return BorePick.stopOf(eye, reachEnd, air, breakable);
    }

    /**
     * Runs the strike steps on every living entity in the tunnel that the
     * where filters keep.
     *
     * @param host   the channel host
     * @param tunnel the tunnel's cells
     */
    private void strikeIn(ChannelHost host, List<BlockPos> tunnel) {
        if (!strike.isEmpty()) {
            host.forEachLivingIn(tunnel, Set.copyOf(where), struck -> new ProgramBehavior(strike).tick(struck));
        }
    }

    /**
     * The blocks a line crosses, nearest its start first, each once.
     *
     * @param from the line's start
     * @param to   the line's end
     * @return the blocks crossed
     */
    static List<BlockPos> blocksAlong(Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int samples = (int) Math.ceil(line.length() / SAMPLE_STEP);
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= samples; i++) {
            BlockPos cell = BlockPos.containing(samples == 0 ? from : from.add(line.scale((double) i / samples)));
            if (cells.isEmpty() || !cells.getLast().equals(cell)) {
                cells.add(cell);
            }
        }
        return cells;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }

    @Override
    public Stream<Step> children() {
        return strike.stream();
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return Step.takenThrough(where, strike, entity);
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return strike.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }
}

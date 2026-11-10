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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rock bore's step, run each held tick of the stream: it cuts a tunnel along
 * the look inside the stream's cone out to its reach, about one block wide,
 * the nearest block first, the JSON's count of breaks a tick, which keeps
 * pace with walking. A solid block outside the tag on the eye line stops the
 * bore there (decision bore-breaks-a-15-degree-cone). Each tick the strike
 * steps run on every living entity whose
 * body overlaps the tunnel's cells and that every where filter keeps, so a
 * mob in the tunnel takes Bore's damage and one behind the stopping block
 * takes none (decision bore-vortex-with-a-worldspace-shake).
 *
 * @param breaks the block tag naming the blocks bore may break
 * @param count  the most blocks one tick breaks
 * @param where  the filters an entity in the tunnel must pass to be struck
 * @param strike the steps run on each struck entity; empty strikes nothing
 */
public record BoreStep(TagKey<Block> breaks, int count, List<EntityFilter> where, List<Step> strike)
        implements Step {

    private static final String NAME = "bore";
    private static final String FIELD_BREAKS = "breaks";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STRIKE = "strike";
    /** The distance between samples along the look, fine enough to visit every block it crosses. */
    private static final double SAMPLE_STEP = 0.05;

    /**
     * Codec for the step's params. The strike list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<BoreStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_BREAKS).forGetter(BoreStep::breaks),
            Codec.INT.fieldOf(FIELD_COUNT).forGetter(BoreStep::count),
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
     * @param count  the most blocks one tick breaks
     */
    public BoreStep(TagKey<Block> breaks, int count) {
        this(breaks, count, List.of(), List.of());
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
            List<BlockPos> tunnel = tunnelOrder(host.eye(), aim, host::airAt, breakable);
            firstBreakable(tunnel, breakable, count).forEach(host::breakBlock);
            strikeIn(host, tunnel);
        });
        return true;
    }

    /**
     * The blocks the bore breaks over the coming ticks while the aim holds,
     * in the order they break, the count a tick: the same pick its tick
     * breaks by, so a client shakes the blocks the server is about to break.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param eye       the eye the tunnel runs from
     * @param aim       the stream's aim: its reach along the look and its cone
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @param ticks     how many ticks ahead to read
     * @return the blocks, nearest the eye first
     */
    public List<BlockPos> breaksAhead(Vec3 eye, ChannelAim aim, Predicate<BlockPos> air,
                                      Predicate<BlockPos> breakable, int ticks) {
        return firstBreakable(tunnelOrder(eye, aim, air, breakable), breakable, count * ticks);
    }

    /**
     * The first breakable cells of an order.
     *
     * @param order     the cells in breaking order
     * @param breakable whether a cell holds a block the step may break
     * @param most      the most cells taken
     * @return the cells, in the order's order
     */
    private static List<BlockPos> firstBreakable(List<BlockPos> order, Predicate<BlockPos> breakable, int most) {
        return order.stream().filter(breakable).limit(most).toList();
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
     * The tunnel's blocks in the order they break, nearest the eye first: the
     * blocks whose centers stand inside the stream's cone, and every block
     * the eye line crosses so the block under the crosshair always breaks,
     * past the eye's own block and short of the first solid block outside
     * the tag on the eye line.
     * decision bore-breaks-a-15-degree-cone
     *
     * @param eye       the eye the tunnel runs from
     * @param aim       the stream's aim: its reach along the look and its cone
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the blocks, each once
     */
    private static List<BlockPos> tunnelOrder(Vec3 eye, ChannelAim aim, Predicate<BlockPos> air,
                                              Predicate<BlockPos> breakable) {
        List<BlockPos> line = blocksAlong(eye, aim.aimPoint());
        int stop = stoppingIndex(line, air, breakable);
        double stopDistance = stop < line.size()
                ? Vec3.atCenterOf(line.get(stop)).distanceTo(eye) : Double.POSITIVE_INFINITY;
        Set<BlockPos> tunnel = new LinkedHashSet<>(line.subList(1, stop));
        for (BlockPos cell : CalcifyStep.blocksInCone(eye, aim.aimPoint(), aim.coneDegrees())) {
            if (!cell.equals(line.getFirst()) && Vec3.atCenterOf(cell).distanceTo(eye) < stopDistance) {
                tunnel.add(cell);
            }
        }
        List<BlockPos> order = new ArrayList<>(tunnel);
        order.sort(Comparator.comparingDouble(cell -> Vec3.atCenterOf(cell).distanceToSqr(eye)));
        return order;
    }

    /**
     * Where the eye line meets the first solid block outside the tag, past
     * the eye's own block.
     *
     * @param line      the blocks the eye line crosses, the eye's own first
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the stopping block's index on the line, or the line's length where none stands
     */
    private static int stoppingIndex(List<BlockPos> line, Predicate<BlockPos> air, Predicate<BlockPos> breakable) {
        for (int i = 1; i < line.size(); i++) {
            if (!air.test(line.get(i)) && !breakable.test(line.get(i))) {
                return i;
            }
        }
        return line.size();
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

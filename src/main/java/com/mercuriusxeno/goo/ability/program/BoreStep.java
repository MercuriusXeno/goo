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
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Rock bore's step, run each held tick of the stream: it drills the blocks
 * the eye line crosses out to the stream's reach, one block wide, whatever
 * the stream's cone. Every breakable block on the line progresses toward
 * broken each tick through the hold's gradual break, the nearest at the pace
 * its JSON names and each one farther out slower, so the line breaks nearest
 * first over several ticks. A solid block outside the tag on the eye line
 * stops the bore there. Each tick the strike steps run on every living entity
 * whose body overlaps the line's cells and that every where filter keeps, so
 * a mob in the tunnel takes Bore's damage and one behind the stopping block
 * takes none (decision bore-vortex-with-a-worldspace-shake).
 * decision bore-breaks-a-15-degree-cone
 * decision bore-drills-the-eye-line-with-steady-progress
 * decision bore-and-flatten-json-carry-two-pace-values
 *
 * @param breaks the block tag naming the blocks bore may break
 * @param pace   how fast the line's blocks progress toward broken
 * @param where  the filters an entity in the tunnel must pass to be struck
 * @param strike the steps run on each struck entity; empty strikes nothing
 */
public record BoreStep(TagKey<Block> breaks, BreakPace pace, List<EntityFilter> where, List<Step> strike)
        implements Step {

    private static final String NAME = "bore";
    private static final String FIELD_BREAKS = "breaks";
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
            BreakPace.CODEC.forGetter(BoreStep::pace),
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
     * @param pace   how fast the line's blocks progress toward broken
     */
    public BoreStep(TagKey<Block> breaks, BreakPace pace) {
        this(breaks, pace, List.of(), List.of());
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
            List<BlockPos> line = eyeLine(host.eye(), aim, host::airAt, breakable);
            host.gradualBreak().progress(pace.ratesNearestFirst(breakableOn(line, breakable)), host);
            strikeIn(host, line);
        });
        return true;
    }

    /**
     * The blocks the bore is progressing while the aim holds: the pick its
     * tick hands the gradual break, so a client shakes exactly the blocks the
     * server is cracking.
     * decision only-the-blocks-about-to-break-shake-briefly-and-gently
     *
     * @param eye       the eye the line runs from
     * @param aim       the stream's aim: its reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the blocks, nearest the eye first
     */
    public static List<BlockPos> progressing(Vec3 eye, ChannelAim aim, Predicate<BlockPos> air,
                                             Predicate<BlockPos> breakable) {
        return breakableOn(eyeLine(eye, aim, air, breakable), breakable);
    }

    private static List<BlockPos> breakableOn(List<BlockPos> line, Predicate<BlockPos> breakable) {
        return line.stream().filter(breakable).toList();
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
     * The blocks the eye line crosses out to the reach, nearest the eye
     * first, past the eye's own block and short of the first solid block
     * outside the tag.
     * decision bore-breaks-a-15-degree-cone
     *
     * @param eye       the eye the line runs from
     * @param aim       the stream's aim: its reach along the look
     * @param air       whether a cell holds air
     * @param breakable whether a cell holds a block in the tag
     * @return the blocks, each once
     */
    private static List<BlockPos> eyeLine(Vec3 eye, ChannelAim aim, Predicate<BlockPos> air,
                                          Predicate<BlockPos> breakable) {
        List<BlockPos> line = blocksAlong(eye, aim.aimPoint());
        return line.subList(1, stoppingIndex(line, air, breakable));
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

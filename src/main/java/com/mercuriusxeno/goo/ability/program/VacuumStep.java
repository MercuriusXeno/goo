package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Void Vacuum's block step, run each held tick of the stream: every slime
 * residual cell inside the cone, goop or slick, breaks whole with its drops,
 * so goop gives back every layer's weird goo and slick its film's kinetic goo;
 * the step converts nothing itself. The cone is the whole volume within the
 * reach and the cone's angle of the look, not the stream's sampled rays, so a
 * held aim takes every residual in front of it; a cell needs a clear line
 * from the eye that only other residuals cross. The break runs as a wave: a
 * cell breaks once the hold is as many ticks old as its distance from the eye
 * sets, nearest first. Before it breaks, a cell decays away through the
 * mingle noise over the wave's last ticks before its own, so the decay fits
 * inside the wave; a cell the wave never reached keeps standing.
 * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
 * vacuumed-residuals-decay-through-mingle-noise-before-breaking
 *
 * @param ticksPerBlock the hold ticks the wave takes to cross one block, so the reach's far end breaks this many
 *                      ticks a block in
 * @param decayTicks    the hold ticks a residual decays over before it breaks
 */
public record VacuumStep(double ticksPerBlock, int decayTicks) implements TakesNoEntity {

    /** The blocks Vacuum takes, goop and slick, so a later residual joins by data. */
    public static final TagKey<Block> SLIME_RESIDUALS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, "slime_residuals"));
    private static final String NAME = "vacuum";
    private static final String FIELD_TICKS_PER_BLOCK = "ticks_per_block";
    private static final String FIELD_DECAY_TICKS = "decay_ticks";
    /** The cell's middle, where the line from the eye is read to. */
    private static final double MIDDLE = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VacuumStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_TICKS_PER_BLOCK).forGetter(VacuumStep::ticksPerBlock),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_DECAY_TICKS).forGetter(VacuumStep::decayTicks)
    ).apply(inst, VacuumStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VacuumStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VacuumStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            Vec3 eye = host.eye();
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                paintReached(host, aim, eye, marks);
            }
            decayPainted(host, eye, marks);
        });
        return true;
    }

    /**
     * Paints each residual in the cone the wave has reached, so its decay starts.
     *
     * @param host  the channel host
     * @param aim   the hold's aim this tick
     * @param eye   the channeling player's eye
     * @param marks the hold's marks
     */
    private void paintReached(ChannelHost host, ChannelAim aim, Vec3 eye, HoldMarks marks) {
        List<BlockPos> reached = residualsInCone(eye, aim.aimPoint().subtract(eye), aim.coneDegrees(),
                pos -> host.blockIn(pos, SLIME_RESIDUALS), pos -> passable(host, pos));
        for (BlockPos cell : reached) {
            if (aim.held() >= decayStartsOn(middleOf(cell).distanceTo(eye))) {
                marks.paint(cell, host.stateAt(cell));
            }
        }
    }

    /**
     * Decays each painted residual one tick toward nothing, the clients eating
     * it away through the mingle noise as a melt is eaten away, and breaks it
     * with its drops once its decay fills. A cell past nine tenths finishes
     * alone after the hold ends; one short of it cools back and stands whole.
     * vacuumed-residuals-decay-through-mingle-noise-before-breaking
     *
     * @param host  the channel host
     * @param eye   the channeling player's eye
     * @param marks the hold's marks
     */
    private void decayPainted(ChannelHost host, Vec3 eye, HoldMarks marks) {
        BlockState nothing = Blocks.AIR.defaultBlockState();
        for (Map.Entry<BlockPos, BlockState> painted : marks.painted().entrySet()) {
            BlockPos cell = painted.getKey();
            BlockState residual = painted.getValue();
            // a cleared cell is unpainted rather than stepped, so goop flowing back into it is taken too
            if (host.stateAt(cell).getBlock() != residual.getBlock()) {
                marks.unpaint(cell);
                continue;
            }
            if (host.finishingAlone(cell)) {
                continue;
            }
            float rate = 1f / decayTicksAt(middleOf(cell).distanceTo(eye));
            Runnable breakAway = () -> breakAway(host, cell, residual, nothing);
            float share = host.exposeBlock(cell, nothing, rate, BlockExposures.MELTS_AWAY);
            if (share >= 1f) {
                breakAway.run();
                marks.unpaint(cell);
            } else if (share > PaintedHold.FINISHES_ALONE) {
                host.finishBlockAlone(cell, rate, breakAway);
            }
        }
    }

    /**
     * Breaks a decayed residual with its drops and tells the clients it went
     * to nothing, so the melt they drew over it ends.
     */
    private static void breakAway(ChannelHost host, BlockPos cell, BlockState residual, BlockState nothing) {
        host.breakBlock(cell);
        host.sendTransform(cell, residual, nothing);
    }

    /**
     * The hold tick a residual's decay starts on, so the decay ends on the
     * tick its distance sets to break on: the decay's ticks come out of the
     * wave's delay rather than on top of it.
     * vacuumed-residuals-decay-through-mingle-noise-before-breaking
     *
     * @param distance the cell's middle's distance from the eye, in blocks
     * @return the hold tick, no earlier than the hold's first
     */
    int decayStartsOn(double distance) {
        return breaksOn(distance, ticksPerBlock) - decayTicksAt(distance) + 1;
    }

    /**
     * The ticks a residual takes to decay: the step's decay, shortened for a
     * cell so near the wave reaches it before a whole decay could run.
     *
     * @param distance the cell's middle's distance from the eye, in blocks
     * @return the decay's ticks, at least one
     */
    private int decayTicksAt(double distance) {
        return Math.max(1, Math.min(decayTicks, breaksOn(distance, ticksPerBlock)));
    }

    /**
     * The hold tick a residual cell breaks on: its distance from the eye times
     * the wave's ticks a block, so the wave runs from the player outward.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param distance      the cell's middle's distance from the eye, in blocks
     * @param ticksPerBlock the hold ticks the wave takes to cross one block
     * @return the hold tick it breaks on, counting the hold's first tick as 1
     */
    static int breaksOn(double distance, double ticksPerBlock) {
        return (int) Math.ceil(distance * ticksPerBlock);
    }

    /**
     * The residual cells inside the cone the eye sees: each cell whose middle
     * stands within the reach and the cone's angle of the look, and whose line
     * from the eye crosses only passable cells or other residuals.
     *
     * @param eye         the cone's apex
     * @param look        the cone's axis, as long as the reach
     * @param coneDegrees the cone's apex angle in degrees
     * @param residual    whether a cell holds a slime residual
     * @param passable    whether a line passes through a cell holding no residual
     * @return the reached cells, nearest the eye first
     */
    static List<BlockPos> residualsInCone(Vec3 eye, Vec3 look, double coneDegrees, Predicate<BlockPos> residual,
                                          Predicate<BlockPos> passable) {
        double range = look.length();
        int reach = (int) Math.ceil(range);
        BlockPos center = BlockPos.containing(eye);
        List<BlockPos> reached = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach),
                center.offset(reach, reach, reach))) {
            if (residual.test(pos) && StreamCone.contains(eye, look, range, coneDegrees, middleOf(pos))
                    && seenPast(eye, pos, cell -> residual.test(cell) || passable.test(cell))) {
                reached.add(pos.immutable());
            }
        }
        reached.sort(Comparator.comparingDouble(pos -> middleOf(pos).distanceToSqr(eye)));
        return reached;
    }

    /**
     * Whether the line from the eye to a cell's middle crosses only cells it passes through.
     *
     * @param eye    the line's start
     * @param target the cell
     * @param passes whether the line passes through a cell
     * @return true where nothing stands between
     */
    private static boolean seenPast(Vec3 eye, BlockPos target, Predicate<BlockPos> passes) {
        for (BlockPos cell : PaintedHold.cellsAlong(eye, middleOf(target))) {
            if (!cell.equals(target) && !passes.test(cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean passable(BlockBreakHost host, BlockPos pos) {
        return host.stateAt(pos).getCollisionShape(host.level(), pos).isEmpty();
    }

    private static Vec3 middleOf(BlockPos pos) {
        return new Vec3(pos.getX() + MIDDLE, pos.getY() + MIDDLE, pos.getZ() + MIDDLE);
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

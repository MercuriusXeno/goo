package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * sets, nearest first, and a cell the cone leaves before its tick keeps
 * standing.
 * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
 *
 * @param ticksPerBlock the hold ticks the wave takes to cross one block, so the reach's far end breaks this many
 *                      ticks a block in
 */
public record VacuumStep(double ticksPerBlock) implements TakesNoEntity {

    /** The blocks Vacuum takes, goop and slick, so a later residual joins by data. */
    public static final TagKey<Block> SLIME_RESIDUALS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, "slime_residuals"));
    private static final String NAME = "vacuum";
    private static final String FIELD_TICKS_PER_BLOCK = "ticks_per_block";
    /** The cell's middle, where the line from the eye is read to. */
    private static final double MIDDLE = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VacuumStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf(FIELD_TICKS_PER_BLOCK).forGetter(VacuumStep::ticksPerBlock)
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
        host.channelAim().filter(ChannelAim::reaching).ifPresent(aim -> {
            Vec3 eye = host.eye();
            List<BlockPos> reached = residualsInCone(eye, aim.aimPoint().subtract(eye), aim.coneDegrees(),
                    pos -> host.blockIn(pos, SLIME_RESIDUALS), pos -> passable(host, pos));
            for (BlockPos cell : reached) {
                if (aim.held() >= breaksOn(middleOf(cell).distanceTo(eye), ticksPerBlock)) {
                    host.breakBlock(cell);
                }
            }
        });
        return true;
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

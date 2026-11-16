package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Void Vacuum's block step, run each held tick of the stream: every world goo
 * cell inside the cone, any goo laid on the volume cell, breaks whole with its
 * drops, so each gives back the goo its layers cost; the step converts
 * nothing itself. The cone is the whole volume within the
 * reach and the cone's angle of the look, not the stream's sampled rays, so a
 * held aim takes every residual in front of it; a cell needs a clear line
 * from the eye that only other residuals cross. The break runs as a wave: a
 * cell breaks whole once the hold is as many ticks old as its distance from
 * the eye sets, nearest first; a cell the wave never reached keeps standing.
 * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
 *
 * @param ticksPerBlock the hold ticks the wave takes to cross one block, so the reach's far end breaks this many
 *                      ticks a block in
 */
public record VacuumStep(double ticksPerBlock) implements TakesNoEntity {

    private static final String NAME = "vacuum";
    /** The break particles a taken cell throws for each layer it held. */
    private static final int BREAK_PARTICLES_PER_LAYER = 3;
    private static final String FIELD_TICKS_PER_BLOCK = "ticks_per_block";
    /** The pitch a residual taken at the eye slurps at. */
    private static final double SLURP_LOW_PITCH = 0.8;
    /** The pitch a residual taken at the reach's far end slurps at. */
    private static final double SLURP_HIGH_PITCH = 1.6;
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
        host.channelAim().filter(ChannelAim::reaching).ifPresent(aim -> breakReached(host, aim, host.eye()));
        return true;
    }

    /**
     * Breaks each residual in the cone the wave has reached this tick, nearest first.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param host the channel host
     * @param aim  the hold's aim this tick
     * @param eye  the channeling player's eye
     */
    private void breakReached(ChannelHost host, ChannelAim aim, Vec3 eye) {
        double reach = aim.aimPoint().distanceTo(eye);
        List<BlockPos> inCone = residualsInCone(eye, aim.aimPoint().subtract(eye), aim.coneDegrees(),
                pos -> worldGoo(host.stateAt(pos)), pos -> passable(host, pos));
        for (BlockPos cell : inCone) {
            double distance = middleOf(cell).distanceTo(eye);
            if (aim.held() >= breaksOn(distance, ticksPerBlock)) {
                breakAway(host, cell, host.stateAt(cell), slurpPitch(distance, reach));
            }
        }
    }

    /**
     * Breaks a reached residual quietly with its drops, throwing three break
     * particles a layer rather than vanilla's whole burst, plays its break
     * sound and slurps it up at its cell.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     * vacuum-throws-three-break-particles-a-layer
     */
    private static void breakAway(ChannelHost host, BlockPos cell, BlockState residual, float slurpPitch) {
        int layers = VolumeCellBlock.layersOf(residual);
        host.breakBlockQuietly(cell);
        host.scatterBreakParticles(cell, residual, layers * BREAK_PARTICLES_PER_LAYER,
                (double) layers / VolumeCellBlock.MAX_LAYERS);
        host.playBreakSound(cell, residual);
        host.slurpAt(cell, slurpPitch);
    }

    /**
     * The pitch a taken residual slurps at: low at the eye, rising evenly
     * along the wave to high at the reach's far end.
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     *
     * @param distance the cell's middle's distance from the eye, in blocks
     * @param reach    the stream's reach, in blocks
     * @return the pitch
     */
    static float slurpPitch(double distance, double reach) {
        double along = reach <= 0 ? 1 : Math.clamp(distance / reach, 0, 1);
        return (float) (SLURP_LOW_PITCH + (SLURP_HIGH_PITCH - SLURP_LOW_PITCH) * along);
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
    public static int breaksOn(double distance, double ticksPerBlock) {
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

    /**
     * Whether a state is a world goo Vacuum takes: any goo laid on the volume
     * cell, so a world goo shipping later is taken the moment it registers.
     * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
     *
     * @param state the state standing in a cell
     * @return true for a volume cell of any goo type
     */
    static boolean worldGoo(BlockState state) {
        return state.getBlock() instanceof VolumeCellBlock;
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

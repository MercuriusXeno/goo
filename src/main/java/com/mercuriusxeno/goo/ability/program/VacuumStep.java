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
 * cell inside the cone, any goo laid on the volume cell, counts the held
 * ticks the cone reaches it and loses one layer each time its count reaches
 * the ticks its distance sets, the near value beside the eye rising evenly
 * to the far value at the reach's far end, floored; each layer drops the goo
 * it is worth, so a cell drained to air gives back what breaking it whole
 * gives. No break lines draw. The cone is the whole volume within the reach
 * and the cone's angle of the look, not the stream's sampled rays, so a held
 * aim takes every residual in front of it; a cell needs a clear line from
 * the eye that only other residuals cross. Releasing, or turning the cone off
 * a cell, leaves it at the layers it has left, and its count is kept for the
 * memory's ticks after the last tick the cone counted on it.
 * vacuum-is-voids-held-cone-that-breaks-residuals-in-a-wave
 * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
 *
 * @param nearLayerTicks   the ticks a layer takes at the eye
 * @param farLayerTicks    the ticks a layer takes at the reach's far end
 * @param countMemoryTicks the ticks a cell keeps its count after the cone last counted on it
 */
public record VacuumStep(int nearLayerTicks, int farLayerTicks, int countMemoryTicks) implements TakesNoEntity {

    private static final String NAME = "vacuum";
    private static final String FIELD_NEAR_LAYER_TICKS = "near_layer_ticks";
    private static final String FIELD_FAR_LAYER_TICKS = "far_layer_ticks";
    private static final String FIELD_COUNT_MEMORY_TICKS = "count_memory_ticks";
    /** The break particles a cell throws for each layer taken from it. */
    private static final int BREAK_PARTICLES_PER_LAYER = 3;
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
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_NEAR_LAYER_TICKS).forGetter(VacuumStep::nearLayerTicks),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_FAR_LAYER_TICKS).forGetter(VacuumStep::farLayerTicks),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_COUNT_MEMORY_TICKS)
                    .forGetter(VacuumStep::countMemoryTicks)
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
        host.channelAim().filter(ChannelAim::reaching).ifPresent(aim -> drainReached(host, aim, host.eye()));
        return true;
    }

    /**
     * Counts this tick on every residual in the cone, nearest first, and
     * takes a layer from each whose count has reached the ticks its distance
     * from where the player stands this tick sets, its count starting over.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param host the channel host
     * @param aim  the hold's aim this tick
     * @param eye  the channeling player's eye
     */
    private void drainReached(ChannelHost host, ChannelAim aim, Vec3 eye) {
        double reach = aim.aimPoint().distanceTo(eye);
        List<BlockPos> inCone = residualsInCone(eye, aim.aimPoint().subtract(eye), aim.coneDegrees(),
                pos -> worldGoo(host.stateAt(pos)), pos -> passable(host, pos));
        for (BlockPos cell : inCone) {
            double distance = middleOf(cell).distanceTo(eye);
            if (host.countVacuumTick(cell, countMemoryTicks) >= layerTicks(distance, reach)) {
                host.forgetVacuumCount(cell);
                takeLayer(host, cell, host.stateAt(cell), slurpPitch(distance, reach));
            }
        }
    }

    /**
     * The held ticks a residual's layer takes: the near value at the eye,
     * rising evenly with its distance to the far value at the reach's far
     * end, floored to whole ticks.
     * vacuum-breaks-a-cell-the-tick-the-wave-reaches-it
     *
     * @param distance the cell's middle's distance from the eye, in blocks
     * @param reach    the stream's reach, in blocks
     * @return the ticks a layer takes
     */
    public int layerTicks(double distance, double reach) {
        return (int) Math.floor(nearLayerTicks + (farLayerTicks - nearLayerTicks) * alongTheReach(distance, reach));
    }

    /**
     * Takes a residual's top layer with the goo it drops, throwing three break
     * particles for it rather than vanilla's whole burst; the tick its last
     * layer goes, it plays its break sound and slurps it up at its cell, once,
     * since a slurp from every cell in the cone every tick would blur into noise.
     * vacuum-throws-three-break-particles-a-layer
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     */
    private static void takeLayer(ChannelHost host, BlockPos cell, BlockState residual, float slurpPitch) {
        int layers = VolumeCellBlock.layersOf(residual);
        host.takeLayer(cell);
        host.scatterBreakParticles(cell, residual, BREAK_PARTICLES_PER_LAYER,
                (double) layers / VolumeCellBlock.MAX_LAYERS);
        if (layers <= 1) {
            host.playBreakSound(cell, residual);
            host.slurpAt(cell, slurpPitch);
        }
    }

    /** How far along the reach a distance stands, 0 at the eye to 1 at the far end. */
    private static double alongTheReach(double distance, double reach) {
        return reach <= 0 ? 1 : Math.clamp(distance / reach, 0, 1);
    }

    /**
     * The pitch a taken residual slurps at: low at the eye, rising evenly
     * with its distance to high at the reach's far end.
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     *
     * @param distance the cell's middle's distance from the eye, in blocks
     * @param reach    the stream's reach, in blocks
     * @return the pitch
     */
    static float slurpPitch(double distance, double reach) {
        return (float) (SLURP_LOW_PITCH + (SLURP_HIGH_PITCH - SLURP_LOW_PITCH) * alongTheReach(distance, reach));
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

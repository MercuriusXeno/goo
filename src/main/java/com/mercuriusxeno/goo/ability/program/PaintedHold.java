package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A held stream that transforms only what the player looks at: each tick it
 * casts rays across its narrow cone, each stopping at the first block it
 * meets, and paints each block a ray meets that faces air and has a block to
 * become; every block the hold has painted builds toward that block while
 * the hold lasts, whether or not the aim is still on it, stepping once full.
 * A painted block past nine tenths of its step is left to finish alone, even after the
 * hold ends; a position the hold has stepped once it passes over (decisions
 * decay-gnats-degrade-each-block-once, broil-heat-wave-ring-cooks-ahead).
 */
final class PaintedHold {

    /**
     * The share past which a painted block finishes its step alone; short of
     * it a released block cools back. The operator's ruling, one share for
     * every held stream: nine tenths.
     */
    static final float FINISHES_ALONE = 0.9f;
    /** Rings of rays off the axis, the last on the cone's rim. */
    private static final int RAY_RINGS = 2;
    /** Rays around each ring. */
    private static final int RAYS_PER_RING = 8;
    /** Points a ray is sampled at per block, fine enough to cross no cell unseen but a corner's sliver. */
    private static final int SAMPLES_PER_BLOCK = 8;
    /** How far past a struck face a ray runs, so the struck block's own cell is reached. */
    private static final double INTO_THE_BLOCK = 0.01;

    private PaintedHold() {
    }

    /**
     * The cells the stream's rays cross from the eye, each ray ending in the
     * first block it meets or at the stream's reach: the air the stream
     * fills and the faces the player sees, never a block hidden behind
     * another. The axis ray runs first, so the block under the crosshair
     * leads.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return each cell reached, once, the axis ray's first
     */
    static Set<BlockPos> seenReach(ChannelHost host, ChannelAim aim) {
        Vec3 eye = host.eye();
        Vec3 look = aim.aimPoint().subtract(eye);
        double range = look.length();
        Set<BlockPos> cells = new LinkedHashSet<>();
        if (range == 0) {
            return cells;
        }
        for (Vec3 ray : rayDirections(look, aim.coneDegrees())) {
            cells.addAll(cellsAlong(eye, rayEnd(host, eye, eye.add(ray.scale(range)))));
        }
        return cells;
    }

    /**
     * The block under the crosshair within the stream's reach.
     *
     * @param host the channel host
     * @param aim  the stream's aim, its reach along the look
     * @return the first block the look meets, foliage included, or empty where it meets none within reach
     */
    static Optional<BlockPos> lookedAtBlock(ChannelHost host, ChannelAim aim) {
        BlockHitResult hit = host.level().clip(new ClipContext(host.eye(), aim.aimPoint(), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getBlockPos()) : Optional.empty();
    }

    /**
     * The directions the stream's rays leave the eye in: the axis, then
     * rings of rays out to the cone's rim.
     *
     * @param axis        the cone's axis, any length
     * @param coneDegrees the cone's apex angle in degrees
     * @return the unit directions, the axis first
     */
    static List<Vec3> rayDirections(Vec3 axis, double coneDegrees) {
        List<Vec3> rays = new ArrayList<>();
        rays.add(axis.normalize());
        for (int ring = 1; ring <= RAY_RINGS; ring++) {
            for (int turn = 0; turn < RAYS_PER_RING; turn++) {
                rays.add(StreamCone.launchDirection(axis, coneDegrees, (double) ring / RAY_RINGS,
                        (double) turn / RAYS_PER_RING));
            }
        }
        return rays;
    }

    /**
     * Where one ray stops: just inside the first block it meets, foliage
     * included, or at its reach where it meets none.
     *
     * @param host  the channel host
     * @param from  the ray's start
     * @param reach the ray's end at the stream's reach
     * @return the ray's end
     */
    private static Vec3 rayEnd(ChannelHost host, Vec3 from, Vec3 reach) {
        BlockHitResult hit = host.level().clip(new ClipContext(from, reach, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return reach;
        }
        return hit.getLocation().add(reach.subtract(from).normalize().scale(INTO_THE_BLOCK));
    }

    /**
     * The cells a line runs through from its start to its end, nearest first.
     *
     * @param from the line's start
     * @param to   the line's end
     * @return each cell the line crosses, once
     */
    static List<BlockPos> cellsAlong(Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        int samples = (int) Math.ceil(line.length() * SAMPLES_PER_BLOCK);
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 0; i <= samples; i++) {
            cells.add(BlockPos.containing(from.add(line.scale(samples == 0 ? 0 : (double) i / samples))));
        }
        return List.copyOf(cells);
    }

    /**
     * Paints each block the stream's rays meet that faces air and has a block to become.
     *
     * @param host  the channel host
     * @param aim   the stream's aim, its reach along the look
     * @param next  the block a block becomes, empty where it becomes none
     * @param marks the hold's marks
     */
    static void paintReach(ChannelHost host, ChannelAim aim, Function<Block, Optional<Block>> next, HoldMarks marks) {
        paintSeen(host, seenReach(host, aim), next, marks);
    }

    /**
     * Paints each reached block that faces air and has a block to become.
     *
     * @param host  the channel host
     * @param seen  the cells the stream's rays reached
     * @param next  the block a block becomes, empty where it becomes none
     * @param marks the hold's marks
     */
    static void paintSeen(ChannelHost host, Collection<BlockPos> seen, Function<Block, Optional<Block>> next,
                          HoldMarks marks) {
        for (BlockPos pos : seen) {
            if (!host.airAt(pos) && next.apply(host.blockAt(pos)).isPresent() && CalcifyStep.facesAir(host, pos)) {
                marks.paint(pos, host.blockAt(pos));
            }
        }
    }

    /**
     * Builds every painted block one tick toward the block it becomes.
     *
     * @param host  the channel host
     * @param next  the block a block becomes, empty where it becomes none
     * @param marks the hold's marks
     * @param rate  the share one tick builds
     * @param tint  the RGB the clients tint the mingled block by, empty for none
     */
    static void buildPainted(ChannelHost host, Function<Block, Optional<Block>> next, HoldMarks marks, float rate,
                             OptionalInt tint) {
        buildPainted(host, next, marks, rate, tint, (pos, raw) -> { });
    }

    /**
     * Builds every painted block one tick toward the block it becomes,
     * telling the caller each block the hold steps. A block stepping to air
     * is never left to finish alone, since only the hold tells the caller.
     *
     * @param host    the channel host
     * @param next    the block a block becomes, empty where it becomes none
     * @param marks   the hold's marks
     * @param rate    the share one tick builds
     * @param tint    the RGB the clients tint the mingled block by, empty for none
     * @param stepped told the position and the block it was, each time the hold steps a block
     */
    static void buildPainted(ChannelHost host, Function<Block, Optional<Block>> next, HoldMarks marks, float rate,
                             OptionalInt tint, BiConsumer<BlockPos, Block> stepped) {
        for (Map.Entry<BlockPos, Block> painted : Map.copyOf(marks.painted()).entrySet()) {
            BlockPos pos = painted.getKey();
            Block raw = painted.getValue();
            if (host.blockAt(pos) != raw) {
                // it finished alone, or something else replaced it
                marks.noteStepped(pos);
            } else if (!host.finishingAlone(pos)) {
                next.apply(raw).ifPresentOrElse(into -> {
                    if (build(host, pos, into, rate, tint)) {
                        marks.noteStepped(pos);
                        stepped.accept(pos, raw);
                    }
                }, () -> marks.unpaint(pos));
            }
        }
    }

    private static boolean build(ChannelHost host, BlockPos pos, Block into, float rate, OptionalInt tint) {
        float share = tint.isPresent()
                ? host.exposeBlock(pos, into.defaultBlockState(), rate, tint.getAsInt())
                : host.exposeBlock(pos, into.defaultBlockState(), rate);
        if (share >= 1f) {
            host.transformBlock(pos, into.defaultBlockState());
            return true;
        }
        if (share > FINISHES_ALONE && !into.defaultBlockState().isAir()) {
            host.finishBlockAlone(pos, rate);
        }
        return false;
    }
}

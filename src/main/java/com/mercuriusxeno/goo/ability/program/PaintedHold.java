package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiPredicate;
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
    /** Points a ray is sampled at per block, fine enough to cross no cell unseen but a corner's sliver. */
    private static final int SAMPLES_PER_BLOCK = 8;

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
        Set<BlockPos> cells = new LinkedHashSet<>();
        if (look.length() == 0) {
            return cells;
        }
        for (Vec3 end : rayEnds(CoatRay.clipping(host.level()), eye, look, aim.coneDegrees())) {
            cells.addAll(cellsAlong(eye, end));
        }
        return cells;
    }

    /**
     * Where each of the stream's rays stops, on its share of the cone's fan.
     * decay-gnats-degrade-each-block-once
     *
     * @param clip        the blocks the rays meet
     * @param eye         the rays' start
     * @param look        the cone's axis, as long as the stream's reach
     * @param coneDegrees the cone's apex angle in degrees
     * @return each ray's end, in the order {@link #rayShares()} names them
     */
    static List<Vec3> rayEnds(CoatRay.BlockClip clip, Vec3 eye, Vec3 look, double coneDegrees) {
        double range = look.length();
        return rayShares().stream().map(share -> CoatRay.end(clip, eye, look, coneDegrees, range, share)).toList();
    }

    /**
     * The places in the cone the stream's rays run: the axis, then rings of
     * rays out to the cone's rim.
     *
     * @return the shares, the axis first
     */
    static List<CoatRay.ConeShare> rayShares() {
        return CoatRay.coneFan();
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
        return rayShares().stream()
                .map(share -> StreamCone.launchDirection(axis, coneDegrees, share.tilt(), share.turn())).toList();
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
                marks.paint(pos, host.stateAt(pos));
            }
        }
    }

    /**
     * The step a painted block takes once full, whether the hold fills it or
     * it finishes alone (decision blocks-that-cook-into-items-cook-like-blocks).
     */
    @FunctionalInterface
    interface CellFinish {

        /**
         * Steps one block.
         *
         * @param pos  the block
         * @param raw  the block painted there
         * @param into the block it becomes
         */
        void finish(BlockPos pos, Block raw, Block into);
    }

    /**
     * The step a painted cell takes once full, for a step whose next state
     * follows the painted state rather than its block alone.
     * slime-is-a-layered-cell-that-levels-out
     */
    @FunctionalInterface
    interface StateFinish {

        /**
         * Steps one cell.
         *
         * @param pos  the cell
         * @param raw  the state painted there
         * @param into the state it becomes
         */
        void finish(BlockPos pos, BlockState raw, BlockState into);
    }

    /**
     * Builds every painted block one tick toward the block it becomes, which
     * it turns into once full.
     *
     * @param host  the channel host
     * @param next  the block a block becomes, empty where it becomes none
     * @param marks the hold's marks
     * @param rate  the share one tick builds
     * @param tint  the RGB the clients tint the mingled block by, empty for none
     */
    static void buildPainted(ChannelHost host, Function<Block, Optional<Block>> next, HoldMarks marks, float rate,
                             OptionalInt tint) {
        buildPainted(host, next, marks, rate, tint,
                (pos, raw, into) -> host.transformBlock(pos, into.defaultBlockState()));
    }

    /**
     * Builds every painted block one tick toward the block it becomes,
     * stepping each through the finish the caller names, the hold's own step
     * and a block left to finish alone alike.
     *
     * @param host   the channel host
     * @param next   the block a block becomes, empty where it becomes none
     * @param marks  the hold's marks
     * @param rate   the share one tick builds
     * @param tint   the RGB the clients tint the mingled block by, empty for none
     * @param finish the step a full block takes
     */
    static void buildPainted(ChannelHost host, Function<Block, Optional<Block>> next, HoldMarks marks, float rate,
                             OptionalInt tint, CellFinish finish) {
        buildPainted(host, next, marks, rate, raw -> tint, finish);
    }

    /**
     * Builds every painted block one tick toward the block it becomes, each
     * block's exposure carrying the tint its painted block names, so one hold
     * can melt one block and cook another
     * (decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold).
     *
     * @param host   the channel host
     * @param next   the block a block becomes, empty where it becomes none
     * @param marks  the hold's marks
     * @param rate   the share one tick builds
     * @param tintOf the tint or mark each painted block's exposure carries, empty for none
     * @param finish the step a full block takes
     */
    static void buildPainted(ChannelHost host, Function<Block, Optional<Block>> next, HoldMarks marks, float rate,
                             Function<Block, OptionalInt> tintOf, CellFinish finish) {
        buildPaintedStates(host, raw -> next.apply(raw.getBlock()).map(Block::defaultBlockState),
                (now, raw) -> now.getBlock() == raw.getBlock(), marks, rate, raw -> tintOf.apply(raw.getBlock()),
                (pos, raw, into) -> finish.finish(pos, raw.getBlock(), into.getBlock()));
    }

    /**
     * Builds every painted cell one tick toward the state it becomes, for a
     * step whose next state follows the painted state rather than its block
     * alone, as a slime cell thickens by a layer.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param host   the channel host
     * @param next   the state a painted state becomes, empty where it becomes none
     * @param stands whether the cell's state now is still the painted one, so the hold builds on; false once it
     *               finished alone or something else replaced it
     * @param marks  the hold's marks
     * @param rate   the share one tick builds
     * @param tintOf the tint or mark each painted state's exposure carries, empty for none
     * @param finish the step a full cell takes
     */
    static void buildPaintedStates(ChannelHost host, Function<BlockState, Optional<BlockState>> next,
                                   BiPredicate<BlockState, BlockState> stands, HoldMarks marks, float rate,
                                   Function<BlockState, OptionalInt> tintOf, StateFinish finish) {
        for (Map.Entry<BlockPos, BlockState> painted : Map.copyOf(marks.painted()).entrySet()) {
            BlockPos pos = painted.getKey();
            BlockState raw = painted.getValue();
            if (!stands.test(host.stateAt(pos), raw)) {
                // it finished alone, or something else replaced it
                marks.noteStepped(pos);
            } else if (!host.finishingAlone(pos)) {
                next.apply(raw).ifPresentOrElse(into -> {
                    if (build(host, pos, into, rate, tintOf.apply(raw), () -> finish.finish(pos, raw, into))) {
                        marks.noteStepped(pos);
                    }
                }, () -> marks.unpaint(pos));
            }
        }
    }

    private static boolean build(ChannelHost host, BlockPos pos, BlockState into, float rate, OptionalInt tint,
                                 Runnable step) {
        float share = tint.isPresent()
                ? host.exposeBlock(pos, into, rate, tint.getAsInt())
                : host.exposeBlock(pos, into, rate);
        if (share >= 1f) {
            step.run();
            return true;
        }
        if (share > FINISHES_ALONE) {
            host.finishBlockAlone(pos, rate, step);
        }
        return false;
    }
}

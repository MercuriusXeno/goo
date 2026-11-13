package com.mercuriusxeno.goo.throwing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One ray of a painting stream's fan: it leaves the eye along a heading
 * inside the cone and ends just inside the first block it meets, foliage
 * included, or at the stream's range where it meets none; Decay, Broil and
 * Petrify paint the cells their rays cross. Goop's and Slick's drips launch
 * along the same fan and meet blocks through the same clip, and their coat
 * lands where each drip's arc strikes.
 * decay-gnats-degrade-each-block-once
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
public final class CoatRay {

    /** How far past a struck face a ray runs, so the struck block's own cell is reached. */
    private static final double INTO_THE_BLOCK = 0.01;
    /** Rings of shares off the axis, the last on the cone's rim. */
    private static final int FAN_RINGS = 2;
    /** Shares around each ring. */
    private static final int SHARES_PER_RING = 8;

    private CoatRay() {
    }

    /**
     * Where a line from one point to another first meets a block.
     */
    @FunctionalInterface
    public interface BlockClip {

        /**
         * The first block face the line meets.
         *
         * @param from the line's start
         * @param to   the line's end
         * @return the strike on that face, empty where the line meets no block
         */
        Optional<BlockStrike> firstHit(Vec3 from, Vec3 to);
    }

    /**
     * Where a line met a block: the point on the struck face, the block, and
     * the side of it struck.
     *
     * @param point the point on the struck face
     * @param block the struck block
     * @param face  the side of the block the line met
     */
    public record BlockStrike(Vec3 point, BlockPos block, Direction face) {
    }

    /**
     * A ray's place in the cone: how far off the axis and how far about it.
     *
     * @param tilt how far off the axis, zero on it and one at the rim
     * @param turn how far about the axis, zero to one for a full turn
     */
    public record ConeShare(double tilt, double turn) {
    }

    /**
     * The clip a level's blocks make, by their outline, fluids passed through.
     *
     * @param level the blocks
     * @return the level's clip
     */
    public static BlockClip clipping(BlockGetter level) {
        return (from, to) -> {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            return hit.getType() == HitResult.Type.BLOCK
                    ? Optional.of(new BlockStrike(hit.getLocation(), hit.getBlockPos(), hit.getDirection()))
                    : Optional.empty();
        };
    }

    /**
     * The places in the cone a stream's fan runs: the axis, then rings of
     * shares out to the cone's rim. A painting stream's rays run them, and Goop's and Slick's drips
     * launch along them.
     *
     * @return the shares, the axis first
     */
    public static List<ConeShare> coneFan() {
        List<ConeShare> shares = new ArrayList<>();
        shares.add(new ConeShare(0, 0));
        for (int ring = 1; ring <= FAN_RINGS; ring++) {
            for (int turn = 0; turn < SHARES_PER_RING; turn++) {
                shares.add(new ConeShare((double) ring / FAN_RINGS, (double) turn / SHARES_PER_RING));
            }
        }
        return shares;
    }

    /**
     * Where the ray for a share of the cone stops.
     *
     * @param clip        the blocks the ray meets
     * @param eye         the ray's start
     * @param look        the cone's axis, any length
     * @param coneDegrees the cone's apex angle in degrees
     * @param range       the stream's reach in blocks
     * @param share       the ray's place in the cone
     * @return just inside the first block the ray meets, or the point at the range where it meets none
     */
    public static Vec3 end(BlockClip clip, Vec3 eye, Vec3 look, double coneDegrees, double range, ConeShare share) {
        Vec3 direction = StreamCone.launchDirection(look, coneDegrees, share.tilt(), share.turn());
        Vec3 reach = eye.add(direction.scale(range));
        return clip.firstHit(eye, reach).map(hit -> hit.point().add(direction.scale(INTO_THE_BLOCK))).orElse(reach);
    }
}

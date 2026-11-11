package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.Optional;

/**
 * One ray of a coating stream's fan: it leaves the eye along a heading
 * inside the cone and ends just inside the first block it meets, foliage
 * included, or at the stream's range where it meets none. The coat paints
 * the cells its rays cross, and Goop's and Slick's motes fly to the same
 * ends, so the cloud lands on the area the stream coats.
 * goop-and-slick-spray-follows-the-coat
 */
public final class CoatRay {

    /** How far past a struck face a ray runs, so the struck block's own cell is reached. */
    private static final double INTO_THE_BLOCK = 0.01;

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
         * @return the point on the struck face, empty where the line meets no block
         */
        Optional<Vec3> firstHit(Vec3 from, Vec3 to);
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
            return hit.getType() == HitResult.Type.BLOCK ? Optional.of(hit.getLocation()) : Optional.empty();
        };
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
        return clip.firstHit(eye, reach).map(hit -> hit.add(direction.scale(INTO_THE_BLOCK))).orElse(reach);
    }
}

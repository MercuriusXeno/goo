package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Result of resolving the player's aim target for goo throwing.
 * Sealed hierarchy: entity hit, block face hit, glow crystal, aimed point,
 * or nothing in range.
 */
public sealed interface TargetResult {

    /**
     * Half-block offset for face center calculations.
     */
    double FACE_CENTER_OFFSET = 0.5;

    /**
     * Singleton for the empty/no-target case.
     */
    TargetResult NONE = new None();

    /**
     * Factory for an entity target aimed at a point on it.
     *
     * @param e     the target entity
     * @param point the point the ray met the entity at
     * @return the result
     */
    static TargetResult entity(Entity e, Vec3 point) {
        return new EntityTarget(e, point);
    }

    /**
     * Factory for a block face target aimed at the face's center.
     *
     * @param pos  the block position
     * @param face the block face direction
     * @return the result
     */
    static TargetResult block(BlockPos pos, Direction face) {
        return new BlockTarget(pos, face, false, faceCenter(pos, face));
    }

    /**
     * Factory for a block face target aimed at an exact point on the face.
     *
     * @param pos   the block position
     * @param face  the block face direction
     * @param point the point the ray met the face at
     * @return the result
     */
    static TargetResult block(BlockPos pos, Direction face, Vec3 point) {
        return new BlockTarget(pos, face, false, point);
    }

    /**
     * Factory for a granny-arc redirected block target, aimed at its top face's center.
     *
     * @param pos the block position
     * @return the result
     */
    static TargetResult grannyArc(BlockPos pos) {
        return new BlockTarget(pos, Direction.UP, true, faceCenter(pos, Direction.UP));
    }

    /**
     * The center of a block's face.
     *
     * @param pos  the block position
     * @param face the face
     * @return the face's center
     */
    static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(FACE_CENTER_OFFSET));
    }

    /**
     * Factory for a glow crystal target.
     *
     * @param pos  the crystal block position
     * @param face the hit face direction
     * @return the result
     */
    static TargetResult glowCrystal(BlockPos pos, Direction face) {
        return new GlowCrystalTarget(pos, face);
    }

    /**
     * Factory for a point the ray found on a block face.
     *
     * @param point the aimed point
     * @param pos   the block the ray met
     * @param face  the face the ray met
     * @return the result
     */
    static TargetResult pointOnBlock(Vec3 point, BlockPos pos, Direction face) {
        return new PointTarget(point, pos, face, true);
    }

    /**
     * Factory for a point in open air, the ray's end at range.
     *
     * @param point the aimed point
     * @return the result
     */
    static TargetResult pointInAir(Vec3 point) {
        return new PointTarget(point, BlockPos.containing(point), Direction.UP, false);
    }

    /**
     * The favored thing's anchor: a block face's center, where the bullseye
     * draws, an entity's center, or a crystal's center.
     *
     * @return the endpoint position, or null for {@link None}
     */
    Vec3 resolveEndpoint();

    /**
     * The exact point aimed at: where the ray met the face or the entity, or
     * the ray's end at range for free aim. The aim line ends here and the
     * throw flies here, while the favored block or entity keeps its outline.
     * aim-point-follows-the-cursor
     *
     * @return the aimed point, or null for {@link None}
     */
    @Nullable Vec3 point();

    /**
     * The player is aiming at a living entity within throw range.
     *
     * @param entity the targeted entity
     * @param point  the point the ray met the entity at
     */
    record EntityTarget(Entity entity, Vec3 point) implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return entity.getBoundingBox().getCenter();
        }
    }

    /**
     * The player is aiming at a specific block face within throw range.
     *
     * @param pos       the targeted block position
     * @param face      the targeted block face
     * @param grannyArc whether the throw is a lob onto a top face
     * @param point     the point the ray met the face at, the top face's center for a lob
     */
    record BlockTarget(BlockPos pos, Direction face, boolean grannyArc, Vec3 point) implements TargetResult {
        /**
         * The center of the struck face of the block's own outline shape, so a
         * partial block's face is named where it stands, not a cube's face above it.
         */
        @Override
        public Vec3 resolveEndpoint() {
            return ShapeFace.at(Minecraft.getInstance() == null ? null : Minecraft.getInstance().level, pos, face)
                    .center();
        }
    }

    /**
     * The player is aiming at a placed glow crystal block. The arc
     * lands at the block center (not the face) so the beam converges
     * on the crystal.
     *
     * @param pos  the crystal block position
     * @param face the hit face (for payload encoding)
     */
    record GlowCrystalTarget(BlockPos pos, Direction face) implements TargetResult {
        /**
         * Points at the crystal's visual center, hugging the attachment face.
         */
        @Override
        public Vec3 resolveEndpoint() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockState state = mc.level.getBlockState(pos);
                if (state.getBlock() instanceof GlowCrystalBlock) {
                    Direction facing = state.getValue(GlowCrystalBlock.FACING);
                    return new Vec3(
                            pos.getX() + FACE_CENTER_OFFSET
                                    - facing.getStepX() * FACE_CENTER_OFFSET,
                            pos.getY() + FACE_CENTER_OFFSET
                                    - facing.getStepY() * FACE_CENTER_OFFSET,
                            pos.getZ() + FACE_CENTER_OFFSET
                                    - facing.getStepZ() * FACE_CENTER_OFFSET);
                }
            }
            return Vec3.atCenterOf(pos);
        }

        @Override
        public Vec3 point() {
            return resolveEndpoint();
        }
    }

    /**
     * The player aims a point in space, favoring no entity or block: where the
     * ray meets a block, or the ray's end at range.
     * target-kind-configured-per-ability
     *
     * @param point   the aimed point
     * @param pos     the block the point lands at, the one the throw names
     * @param face    the face the ray met, UP for a point in open air
     * @param onBlock whether the ray met a block, which then shows its world indicator
     */
    record PointTarget(Vec3 point, BlockPos pos, Direction face, boolean onBlock) implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return point;
        }

        /**
         * The block face the point sits on, as a block target.
         *
         * @return the block target the point's tile reads as
         */
        public BlockTarget tile() {
            return new BlockTarget(pos, face, false, point);
        }
    }

    /**
     * Nothing targetable within throw range.
     */
    record None() implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return null;
        }

        @Override
        public @Nullable Vec3 point() {
            return null;
        }
    }
}

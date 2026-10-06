package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Resolves what the player aims at within throw range, filtered by the hint
 * the selected ability's badge names: a mob ability aims through the aim
 * assist, a world one at the block face the reticle meets, a free or
 * channeled one at the ray's point. It runs
 * once per client tick for {@link AimState} (decision
 * render-context-is-the-one-emitter).
 */
final class AimTargets {

    /**
     * Granny-arc threshold: when a side-face hit lands in the upper 15% of
     * the shape's height, targeting redirects to the UP face so the goo
     * arcs onto the top of the block instead of hitting the side.
     */
    private static final double GRANNY_ARC_THRESHOLD = 0.85;

    private AimTargets() {
    }

    /**
     * Resolves the aim for the given hint from the player's eye and look as
     * the frame draws them (decision aim-target-follows-client-aim).
     *
     * @param player      the local player
     * @param seed        the previous frame's aim-assist hit, the sticky seed
     * @param hint        the targeting mode from the selected ability
     * @param partialTick the frame's partial tick
     * @return the target and the aim-assist hit behind it
     */
    static AimState.Resolution resolve(Player player, AimAssistResolver.@Nullable AimHit seed,
                                       TargetingHint hint, float partialTick) {
        if (hint == TargetingHint.NONE) {
            return AimState.Resolution.NOTHING;
        }
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 reach = eyePos.add(player.getViewVector(partialTick).scale(AimState.MAX_RANGE));
        return resolveFor(hint, new AimSources() {
            @Override
            public AimAssistResolver.@Nullable AimHit entityHit() {
                return AimAssistResolver.findClosestAimHit(player, eyePos, reach, seed);
            }

            @Override
            public TargetResult blockTarget() {
                return resolveBlockTarget(player, eyePos, reach);
            }

            @Override
            public TargetResult pointTarget() {
                return pointOf(clipBlocks(player, eyePos, reach), reach);
            }
        });
    }

    /**
     * Where one frame's aim can come from, each read only when the hint asks for it.
     */
    interface AimSources {
        /**
         * The aim assist's entity hit.
         *
         * @return the hit, or null when no entity is near the ray
         */
        AimAssistResolver.@Nullable AimHit entityHit();

        /**
         * The block face the ray meets, projected to the ground on a miss.
         *
         * @return the block target, or NONE
         */
        TargetResult blockTarget();

        /**
         * The ray's point.
         *
         * @return the point target
         */
        TargetResult pointTarget();
    }

    /**
     * Resolves the aim the hint asks for: an entity favors the aim assist and
     * falls back to the block so the aim never reads NONE, a block favors the
     * block face, a point aims the ray's point, and NONE aims nothing.
     * target-kind-configured-per-ability
     *
     * @param hint    the aim mode from the selected ability's badge
     * @param sources where the aim comes from
     * @return the target and the aim-assist hit behind it
     */
    static AimState.Resolution resolveFor(TargetingHint hint, AimSources sources) {
        return switch (hint) {
            case NONE -> AimState.Resolution.NOTHING;
            case BLOCK -> new AimState.Resolution(sources.blockTarget(), null);
            case POINT -> new AimState.Resolution(sources.pointTarget(), null);
            case ENTITY -> {
                AimAssistResolver.AimHit hit = sources.entityHit();
                yield hit instanceof AimAssistResolver.AimHit.EntityHit eh
                        ? new AimState.Resolution(TargetResult.entity(eh.entity()), hit)
                        : new AimState.Resolution(sources.blockTarget(), null);
            }
        };
    }

    /**
     * The point a ray aims: where it meets a block, on the face it met, or its
     * end at range in open air.
     *
     * @param hit   the ray's block clip
     * @param reach the ray's end at range
     * @return the point target
     */
    static TargetResult pointOf(BlockHitResult hit, Vec3 reach) {
        if (hit.getType() == HitResult.Type.BLOCK) {
            return TargetResult.point(hit.getLocation(), hit.getBlockPos(), hit.getDirection());
        }
        return TargetResult.point(reach, BlockPos.containing(reach), Direction.UP);
    }

    /**
     * Clips the ray against block outlines and fluid sources.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the ray's end at range
     * @return the clip result
     */
    private static BlockHitResult clipBlocks(Player player, Vec3 eyePos, Vec3 reach) {
        return player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    /**
     * Clips against blocks and returns a block or granny-arc target.
     * On a miss, projects to max range along the look vector so the
     * arc always renders toward the aimed direction.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @return the resolved block target, or max-range projection on miss
     */
    private static TargetResult resolveBlockTarget(Player player, Vec3 eyePos, Vec3 reach) {
        BlockHitResult hit = clipBlocks(player, eyePos, reach);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return projectToGround(player.level(), reach);
        }
        return classifyBlockHit(player.level(), hit);
    }

    /**
     * Raycasts down from the max-range endpoint to find the ground.
     * If the endpoint is above max build height (looking upward), starts
     * the downward cast from build height at the same XZ.
     *
     * @param level the current level
     * @param reach the max-range endpoint along the look vector
     * @return a block target on the ground, or NONE if no ground found
     */
    private static TargetResult projectToGround(Level level, Vec3 reach) {
        double topY = Math.min(reach.y, level.getMaxY());
        Vec3 top = new Vec3(reach.x, topY, reach.z);
        Vec3 bottom = new Vec3(reach.x, level.getMinY(), reach.z);
        BlockHitResult ground = level.clip(new ClipContext(
                top, bottom, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, CollisionContext.empty()));
        if (ground.getType() != HitResult.Type.BLOCK) {
            return TargetResult.NONE;
        }
        return TargetResult.block(ground.getBlockPos(), Direction.UP);
    }

    /**
     * Classifies a confirmed block hit as a granny-arc or normal face target.
     * A granny arc is only offered when the block directly above the hit is
     * air - otherwise the arc would collide with that block and the shot
     * makes no sense, so it falls back to a normal side-face target.
     *
     * @param level the current level
     * @param hit   the confirmed block hit
     * @return granny-arc or block-face target result
     */
    private static TargetResult classifyBlockHit(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (level.getBlockState(pos).getBlock() instanceof GlowCrystalBlock) {
            return TargetResult.glowCrystal(pos, face);
        }
        BlockPos adj = pos.relative(face);
        if (isGlowCrystalOnFace(level, adj, face)) {
            return TargetResult.glowCrystal(adj, face);
        }
        if (isGrannyArcCandidate(level, hit, pos, face)) {
            return TargetResult.grannyArc(pos);
        }
        return TargetResult.block(pos, face);
    }

    /**
     * True when the hit qualifies for a granny-arc: side face, upper edge, air above.
     *
     * @param level the current level
     * @param hit   the block hit result
     * @param pos   the hit block position
     * @param face  the hit face direction
     * @return true if the hit qualifies for a granny arc
     */
    private static boolean isGrannyArcCandidate(Level level, BlockHitResult hit,
                                                BlockPos pos, Direction face) {
        return face.getAxis() != Direction.Axis.Y
                && isUpperEdge(level, hit)
                && level.getBlockState(pos.above()).isAir();
    }

    /**
     * Returns true if the block at {@code pos} is a glow crystal whose
     * facing matches the given face (i.e. it is attached to that face).
     *
     * @param level the current level
     * @param pos   the candidate crystal position
     * @param face  the face direction to match
     * @return true if a matching glow crystal exists
     */
    private static boolean isGlowCrystalOnFace(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof GlowCrystalBlock
                && state.getValue(GlowCrystalBlock.FACING) == face;
    }

    /**
     * Returns true if the hit landed in the upper portion of the block's
     * voxel shape, measured against the shape's actual Y extent so slabs,
     * stairs, etc. use their real geometry, not a full cube.
     *
     * @param level the current level
     * @param hit   the block hit result
     * @return true if the hit is on the upper edge
     */
    private static boolean isUpperEdge(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        AABB bounds = shape.bounds();
        double range = bounds.maxY - bounds.minY;
        if (range <= 0) {
            return false;
        }
        double hitY = hit.getLocation().y - pos.getY();
        double relative = (hitY - bounds.minY) / range;
        return relative >= GRANNY_ARC_THRESHOLD;
    }
}

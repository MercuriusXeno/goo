package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
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
 * Resolves what the player aims at within throw range, filtered by the
 * selected ability's hint: an entity-tagged ability aims through the aim
 * assist, a block-tagged one at the block face the reticle meets. It runs
 * once per client tick for {@link AimState} (decision
 * render-context-is-the-one-emitter).
 */
final class AimTargets {

    /**
     * Granny-arc threshold: when a side-face hit lands in the upper 15% of
     * the shape's height, targeting redirects to the UP face so the blob
     * arcs onto the top of the block instead of hitting the side.
     */
    private static final double GRANNY_ARC_THRESHOLD = 0.85;

    private AimTargets() {
    }

    /**
     * Resolves the aim for the given hint from the player's eye at the end of the tick.
     *
     * @param player the local player
     * @param seed   the previous tick's aim-assist hit, the sticky seed
     * @param hint   the targeting mode from the selected ability
     * @return the target and the aim-assist hit behind it
     */
    static AimState.Resolution resolve(Player player, AimAssistResolver.@Nullable AimHit seed,
                                       TargetingHint hint) {
        if (hint == TargetingHint.NONE) {
            return AimState.Resolution.NOTHING;
        }
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 reach = eyePos.add(player.getViewVector(1.0f).scale(AimState.MAX_RANGE));
        String abilityId = GloveAim.selectedAbilityId(player);
        if (hint == TargetingHint.ENTITY) {
            AimAssistResolver.AimHit hit = AimAssistResolver.findClosestAimHit(player, eyePos, reach, seed, abilityId);
            return new AimState.Resolution(targetOf(hit), hit);
        }
        return new AimState.Resolution(resolveBlockTarget(player, eyePos, reach, abilityId), null);
    }

    /**
     * The target an aim-assist hit names.
     *
     * @param hit the aim-assist hit, or null
     * @return an entity or chain marker target, or NONE
     */
    private static TargetResult targetOf(AimAssistResolver.@Nullable AimHit hit) {
        if (hit instanceof AimAssistResolver.AimHit.EntityHit eh) {
            return TargetResult.entity(eh.entity());
        }
        if (hit instanceof AimAssistResolver.AimHit.ChainMarkerHit cmh) {
            return TargetResult.chainMarker(cmh.pos());
        }
        return TargetResult.NONE;
    }

    /**
     * Clips against blocks and returns a block or granny-arc target.
     * On a miss, projects to max range along the look vector so the
     * arc always renders toward the aimed direction.
     *
     * @param player the local player
     * @param eyePos the eye position
     * @param reach  the maximum reach endpoint
     * @param abilityId the glove's selected ability id
     * @return the resolved block target, or max-range projection on miss
     */
    private static TargetResult resolveBlockTarget(Player player, Vec3 eyePos, Vec3 reach,
                                                   @Nullable String abilityId) {
        BlockHitResult hit = player.level().clip(new ClipContext(
                eyePos, reach, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return projectToGround(player.level(), reach);
        }
        return classifyBlockHit(player.level(), hit, abilityId);
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
     * @param abilityId the glove's selected ability id; a marker of another reads as a solid block
     * @return granny-arc or block-face target result
     */
    private static TargetResult classifyBlockHit(Level level, BlockHitResult hit, @Nullable String abilityId) {
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (TargetBlockReads.isKeyedMarker(level, pos, abilityId)) {
            return TargetResult.chainMarker(pos);
        }
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

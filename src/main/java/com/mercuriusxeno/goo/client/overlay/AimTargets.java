package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
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
import java.util.List;
import java.util.function.Supplier;

/**
 * Resolves what the player aims at within throw range, filtered by the hint
 * the selected ability's badge names: a mob ability aims through the aim
 * assist, a world one at the block face the reticle meets, a free or
 * channeled one at the ray's point. It runs
 * once per frame for {@link AimState} (decision
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
     * The aim ray: where it starts and where it ends at range.
     *
     * @param origin the ray's start, the camera as the frame draws it
     * @param reach  the ray's end at range
     */
    record AimRay(Vec3 origin, Vec3 reach) {

        /**
         * The ray from an origin along a look, out to range.
         * decision grab-reaches-telekinesis-range: a grab marks no mob beyond what it lifts
         *
         * @param origin the ray's start
         * @param look   the unit look vector
         * @param range  how far the ray reaches, the selected ability's reach (GloveAim.aimRange)
         * @return the ray
         */
        static AimRay along(Vec3 origin, Vec3 look, double range) {
            return new AimRay(origin, origin.add(look.scale(range)));
        }
    }

    /**
     * Clips a ray segment against the world's blocks.
     */
    @FunctionalInterface
    interface BlockClip {
        /**
         * Clips the segment.
         *
         * @param from the segment's start
         * @param to   the segment's end
         * @return the clip result
         */
        BlockHitResult clip(Vec3 from, Vec3 to);
    }

    /**
     * Resolves the aim for the given hint along the ray the caller passes,
     * the camera's as the frame draws it, so the reticule eases with the view
     * as the vanilla crosshair does.
     * decision aim-target-follows-client-aim
     * decision aim-ray-starts-at-the-camera
     *
     * @param player    the local player
     * @param ray       the aim ray from the camera
     * @param seed      the previous frame's aim-assist hit, the sticky seed
     * @param hint      the targeting mode from the selected ability
     * @param behaviors the selected ability's steps, which name the entities the aim may outline
     * @return the target and the aim-assist hit behind it
     */
    static AimState.Resolution resolve(Player player, AimRay ray, AimAssistResolver.@Nullable AimHit seed,
                                       TargetingHint hint, List<Step> behaviors) {
        if (hint == TargetingHint.NONE) {
            return AimState.Resolution.NOTHING;
        }
        BlockClip clip = (from, to) -> clipBlocks(player, from, to);
        return resolveFor(hint, new AimSources() {
            @Override
            public AimAssistResolver.@Nullable AimHit entityHit() {
                return AimAssistResolver.findClosestAimHit(player, ray.origin(), ray.reach(), seed, behaviors);
            }

            @Override
            public TargetResult blockTarget() {
                return resolveBlockTarget(player.level(), ray, clip);
            }

            @Override
            public TargetResult pointTarget() {
                return pointAlong(ray, clip);
            }

            @Override
            public Vec3 entityPoint(Entity entity) {
                return pointOnEntity(entity.getBoundingBox(), ray.origin(), ray.reach());
            }
        });
    }

    /**
     * The point a ray aims: where it meets a block, or its end at range.
     *
     * @param ray  the aim ray
     * @param clip the block clip
     * @return the point target
     */
    static TargetResult pointAlong(AimRay ray, BlockClip clip) {
        return pointOf(clip.clip(ray.origin(), ray.reach()), ray.reach());
    }

    /**
     * The point the ray meets an entity's box at, or the box's center where
     * the aim assist chose an entity the ray passes beside.
     * aim-point-follows-the-cursor
     *
     * @param box   the entity's bounding box
     * @param from  the ray's start, the camera
     * @param reach the ray's end at range
     * @return the aimed point on the entity
     */
    static Vec3 pointOnEntity(AABB box, Vec3 from, Vec3 reach) {
        return box.clip(from, reach).orElse(box.getCenter());
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

        /**
         * The point the ray meets an entity at.
         *
         * @param entity the entity the aim assist chose
         * @return the aimed point on it
         */
        Vec3 entityPoint(Entity entity);
    }

    /**
     * Resolves the aim the hint asks for: an entity takes the mob the aim
     * assist finds and nothing where none is near, so a mob ability is never
     * thrown at the world, a block favors the
     * block face, a point locks onto a mob near the ray and aims the ray's
     * point where none is near, and NONE aims nothing.
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
            case POINT -> lockOnOr(sources, sources::pointTarget);
            case ENTITY -> lockOnOr(sources, () -> TargetResult.NONE);
        };
    }

    /**
     * Locks onto the mob the aim assist finds near the ray, aimed at the point
     * the ray meets it, or falls back where no mob is near: a mob ability to
     * nothing, a free aim to the ray's point, so free aim keeps its mob lock-on.
     * target-kind-configured-per-ability
     * aim-point-follows-the-cursor
     *
     * @param sources  where the aim comes from
     * @param fallback the target where no mob is near
     * @return the target and the aim-assist hit behind it
     */
    private static AimState.Resolution lockOnOr(AimSources sources, Supplier<TargetResult> fallback) {
        AimAssistResolver.AimHit hit = sources.entityHit();
        if (hit instanceof AimAssistResolver.AimHit.EntityHit eh) {
            return new AimState.Resolution(TargetResult.entity(eh.entity(), sources.entityPoint(eh.entity())), hit);
        }
        return new AimState.Resolution(fallback.get(), null);
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
            return TargetResult.pointOnBlock(hit.getLocation(), hit.getBlockPos(), hit.getDirection());
        }
        return TargetResult.pointInAir(reach);
    }

    /**
     * Clips the ray against block outlines and fluid sources.
     *
     * @param player the local player
     * @param from   the ray's start
     * @param to     the ray's end at range
     * @return the clip result
     */
    private static BlockHitResult clipBlocks(Player player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
    }

    /**
     * Clips against blocks and returns a block or granny-arc target.
     * On a miss, projects to max range along the look vector so the
     * arc always renders toward the aimed direction.
     *
     * @param level the current level
     * @param ray   the aim ray
     * @param clip  the block clip
     * @return the resolved block target, or max-range projection on miss
     */
    private static TargetResult resolveBlockTarget(Level level, AimRay ray, BlockClip clip) {
        BlockHitResult hit = clip.clip(ray.origin(), ray.reach());
        if (hit.getType() != HitResult.Type.BLOCK) {
            return projectToGround(level, ray.reach());
        }
        return classifyBlockHit(level, hit);
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
        return TargetResult.block(ground.getBlockPos(), Direction.UP, ground.getLocation());
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
        return TargetResult.block(pos, face, hit.getLocation());
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

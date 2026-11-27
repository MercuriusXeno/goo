package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Target resolution with two-pass aim assist (exact raytrace + cone scan)
 * and sticky retention to prevent flicker at cone edges.
 *
 * <p>Handles the entities the selected ability's steps take (decision
 * splat-runs-the-program-no-fuse).
 */
final class AimAssistResolver {

    /**
     * The shared aim-assist forgiveness half-angle in degrees. Targets within
     * this cone around the crosshair are eligible even if the exact raytrace
     * misses their hitbox.
     */
    static final double AIM_ASSIST_DEGREES = 4.0;

    /**
     * How far past the pick cone, in degrees, the sticky retention cone
     * reaches. Once locked, a target stays selected until the crosshair drifts
     * beyond this wider cone, preventing flicker when the cursor is near the edge.
     */
    private static final double STICKY_PAST_THE_CONE = 1.0;

    /**
     * Half divisor for centering AABB calculations.
     */
    private static final double CENTER_HALF = 0.5;

    /**
     * Sentinel cosine value indicating an invalid or out-of-range cone angle.
     */
    private static final double NO_CONE_ANGLE = -1;

    /**
     * Inset from AABB face to avoid sampling right at block boundaries.
     */
    private static final double LOS_INSET = 0.05;

    /**
     * Axis sample index: west face center.
     */
    private static final int AXIS_WEST = 0;
    /**
     * Axis sample index: east face center.
     */
    private static final int AXIS_EAST = 1;
    /**
     * Axis sample index: north face center.
     */
    private static final int AXIS_NORTH = 2;
    /**
     * Axis sample index: south face center.
     */
    private static final int AXIS_SOUTH = 3;

    private AimAssistResolver() {
    }

    /**
     * Finds the best aim hit (an entity the ability takes) using a
     * two-pass approach plus sticky retention on the previous hit.
     *
     * <ol>
     *   <li>Exact raytrace - if the crosshair directly clips a candidate AABB,
     *       pick the nearest by distance.</li>
     *   <li>Cone scan - pick the candidate with highest cosine to the
     *       crosshair.</li>
     *   <li>Sticky - if the previous hit is still within the wider sticky
     *       cone, keep it.</li>
     * </ol>
     * <p>
     * All passes require line-of-sight, and every pass takes a candidate only
     * where its aimed point, where the ray clips it or its center, lies
     * within range of the eye (decision
     * diagnose-and-correct-the-indicator-disagreeing-with-the-cast).
     *
     * @param player    the interacting player
     * @param ray       the aim ray, from the camera, and the measure its candidates pass
     * @param previous  the previous frame's hit, or null
     * @param behaviors the selected ability's steps, which name what may be hit
     * @return the best hit, or null if none in range/cone
     */
    static @Nullable AimHit findClosestAimHit(Player player, AimTargets.AimRay ray,
                                              @Nullable AimHit previous, List<Step> behaviors) {
        Vec3 from = ray.origin();
        Vec3 to = ray.reach();
        double coneDegrees = pickConeDegrees(behaviors);
        List<Entity> entities = gatherCandidates(player, from, to, behaviors, coneDegrees);
        List<AimHit> candidates = asHits(entities);
        AimHit exact = findExactHit(candidates, player, ray);
        if (exact != null) {
            return exact;
        }

        Predicate<AimHit> inSight = candidate -> isInSight(candidate, player, from);
        AimHit bestCone = findBestConeHit(candidates, ray, Math.cos(Math.toRadians(coneDegrees)), inSight);
        double stickyCos = Math.cos(Math.toRadians(coneDegrees + STICKY_PAST_THE_CONE));
        AimHit sticky = retainSticky(candidates, ray, bestCone, previous, stickyCos, inSight);
        return (sticky != null) ? sticky : bestCone;
    }

    /**
     * The half-angle of the cone the selected ability picks in: its Grab's
     * own where it holds one, else the shared cone. The outline draws what
     * this cone picks, so the outlined mob is the one right click acts on.
     * decision grab-cone-picks-more-aggressively-than-other-goo-types
     *
     * @param behaviors the selected ability's steps
     * @return the half-angle, in degrees
     */
    static double pickConeDegrees(List<Step> behaviors) {
        return GrabStep.pickConeOf(behaviors).orElse(AIM_ASSIST_DEGREES);
    }

    /**
     * Gathers the candidates within the aim-assist cone's bounding volume.
     *
     * @param player    the aiming player (excluded from results)
     * @param from      ray start (eye position)
     * @param to        ray end (eye + look * range)
     * @param behaviors   the selected ability's steps
     * @param coneDegrees the pick cone's half-angle
     * @return candidate entities in the inflated search box
     */
    private static List<Entity> gatherCandidates(Player player, Vec3 from, Vec3 to, List<Step> behaviors,
            double coneDegrees) {
        AABB searchBox = buildSearchBox(from, to, coneDegrees);
        boolean sparesOwnMinions = GloveAim.sparesOwnMinions(player);
        return player.level().getEntities(player, searchBox, e -> isCandidate(e, behaviors, player, sparesOwnMinions));
    }

    /**
     * Whether the aim may outline an entity: one the selected ability's
     * steps take, pickable or an item entity, which vanilla never picks.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity    the entity in the search box
     * @param behaviors the selected ability's steps
     * @return true where the entity is a candidate
     */
    static boolean isCandidate(Entity entity, List<Step> behaviors) {
        return (entity.isPickable() || entity instanceof ItemEntity) && Step.takenByAny(behaviors, entity);
    }

    /**
     * Whether the aim may outline an entity, sparing the player's own
     * minions where the selected ability spares them, so a harmful mob
     * ability never locks on a mob its caster led or charmed, and
     * dropping a mob the selected ability's steps refuse for the player,
     * so a refused Lead or Charm never locks on it.
     * mob-targeted-attacks-refuse-your-minions
     * refused-rally-or-charm-spends-no-goo
     *
     * @param entity           the entity in the search box
     * @param behaviors        the selected ability's steps
     * @param player           the aiming player
     * @param sparesOwnMinions whether the selected ability spares the player's own minions
     * @return true where the entity is a candidate
     */
    static boolean isCandidate(Entity entity, List<Step> behaviors, Entity player, boolean sparesOwnMinions) {
        return isCandidate(entity, behaviors) && !(sparesOwnMinions && OwnMinions.ownMinionOf(player, entity))
                && !Step.refusedByAny(behaviors, player, entity);
    }

    /**
     * Builds the search box candidates are gathered in, swept along the ray
     * from its own start, so a leg the aim carries on along out of a linked
     * gate's partner gathers the mobs beside that leg wherever the partner
     * stands. aim-and-shots-carry-through-linked-gates
     *
     * @param from        ray start
     * @param to          ray end
     * @param coneDegrees the pick cone's half-angle
     * @return the inflated search box
     */
    private static AABB buildSearchBox(Vec3 from, Vec3 to, double coneDegrees) {
        return new AABB(from, from)
                .expandTowards(to.subtract(from))
                .inflate(searchConeRadius(from, to, coneDegrees) + 1.0);
    }

    /**
     * How wide the pick cone opens at the aim ray's end, the ray reaching
     * the selected ability's own range.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     *
     * @param from        ray start
     * @param to          ray end
     * @param coneDegrees the pick cone's half-angle
     * @return the cone's radius at the ray's end, in blocks
     */
    static double searchConeRadius(Vec3 from, Vec3 to, double coneDegrees) {
        return from.distanceTo(to) * Math.tan(Math.toRadians(coneDegrees));
    }

    /**
     * Every candidate as an aim hit.
     *
     * @param entities entity candidates
     * @return the candidates
     */
    private static List<AimHit> asHits(List<Entity> entities) {
        List<AimHit> hits = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            hits.add(new AimHit.EntityHit(entity));
        }
        return hits;
    }

    /**
     * Finds the nearest candidate the crosshair's ray clips, in sight.
     *
     * @param candidates the candidates in tie-break order
     * @param player     the aiming player
     * @param ray        the aim ray
     * @return the closest exact hit, or null
     */
    private static @Nullable AimHit findExactHit(List<AimHit> candidates, Player player, AimTargets.AimRay ray) {
        Vec3 from = ray.origin();
        double bestDist = Double.MAX_VALUE;
        AimHit best = null;
        for (AimHit candidate : candidates) {
            Optional<Vec3> clip = candidate.pickBox().clip(from, ray.reach());
            if (clip.isEmpty() || !ray.reaches(clip.get())) {
                continue;
            }
            double dist = from.distanceToSqr(clip.get());
            if (dist < bestDist && isInSight(candidate, player, from)) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Finds the candidate closest to the crosshair inside the pick cone.
     *
     * @param candidates the candidates in tie-break order
     * @param ray        the aim ray
     * @param coneCos    the cosine of the pick cone's half-angle
     * @param inSight    whether the player sees a candidate
     * @return the cone-pass winner, or null if nothing clears the cone
     */
    static @Nullable AimHit findBestConeHit(List<AimHit> candidates, AimTargets.AimRay ray,
                                            double coneCos, Predicate<AimHit> inSight) {
        double bestCos = coneCos;
        AimHit best = null;
        for (AimHit candidate : candidates) {
            double cos = coneCosine(candidate, ray, coneCos, inSight);
            if (cos > bestCos) {
                bestCos = cos;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Keeps the previous hit while it is still a live candidate inside the
     * wider sticky cone, to prevent flicker at the cone's edge.
     *
     * @param candidates the candidates this frame
     * @param ray        the aim ray
     * @param bestCone   the cone-pass winner this frame
     * @param previous   the previous hit, or null
     * @param stickyCos  the cosine of the sticky cone's half-angle
     * @param inSight    whether the player sees a candidate
     * @return the previous hit if it is retained, or null
     */
    private static @Nullable AimHit retainSticky(List<AimHit> candidates, AimTargets.AimRay ray,
                                                 @Nullable AimHit bestCone, @Nullable AimHit previous,
                                                 double stickyCos, Predicate<AimHit> inSight) {
        if (previous == null || previous.equals(bestCone)
                || !previous.isAlive() || !candidates.contains(previous)) {
            return null;
        }
        return coneCosine(previous, ray, stickyCos, inSight) > stickyCos ? previous : null;
    }

    /**
     * The cosine between the look direction and the candidate's center when
     * the candidate's center lies within range of the eye, inside the given
     * cone and in sight.
     *
     * @param candidate the candidate
     * @param ray       the aim ray
     * @param coneCos   the cosine of the cone's half-angle
     * @param inSight   whether the player sees a candidate
     * @return the cosine, or NO_CONE_ANGLE when out of range, outside the cone or occluded
     */
    private static double coneCosine(AimHit candidate, AimTargets.AimRay ray, double coneCos,
                                     Predicate<AimHit> inSight) {
        Vec3 center = candidate.box().getCenter();
        if (!inReach(ray.measuredFrom().distanceTo(center), ray.rangeLeft())) {
            return NO_CONE_ANGLE;
        }
        Vec3 lookDir = ray.reach().subtract(ray.origin()).normalize();
        double cos = lookDir.dot(center.subtract(ray.origin()).normalize());
        return cos > coneCos && inSight.test(candidate) ? cos : NO_CONE_ANGLE;
    }

    /**
     * Whether a candidate's center stands within range of the eye, past the
     * eye's own box: the cone marks no mob beyond range, so a
     * grab marks none it cannot lift (decision grab-reaches-telekinesis-range).
     *
     * @param distance the distance from the eye to the candidate's center
     * @param range    the range left to the measure
     * @return true where the cone may take the candidate
     */
    static boolean inReach(double distance, double range) {
        return distance > CENTER_HALF && distance <= range;
    }

    /**
     * Whether the player sees the candidate, its own block exempt from occluding it.
     *
     * @param candidate the candidate
     * @param player    the aiming player
     * @param from      the eye position
     * @return true if any sample ray reaches it
     */
    private static boolean isInSight(AimHit candidate, Player player, Vec3 from) {
        return hasLineOfSightToBox(player.level(), player, from, candidate.box(), candidate.selfBlock());
    }

    /**
     * Line-of-sight to an arbitrary AABB. If {@code selfBlock} is non-null,
     * rays that terminate inside that block position are treated as clear
     * (prevents a target block's own voxel from occluding its own LOS).
     *
     * @param level     the current level
     * @param player    the interacting player
     * @param eyePos    the eye position
     * @param box       the target AABB
     * @param selfBlock block position to exempt from occlusion, or null
     * @return true if any sample ray reaches the target
     */
    private static boolean hasLineOfSightToBox(Level level, Player player, Vec3 eyePos,
                                               AABB box, @Nullable BlockPos selfBlock) {
        Vec3[] samples = buildLosSamples(box);
        for (Vec3 sample : samples) {
            if (isClearRay(level, player, eyePos, sample, selfBlock)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if a ray from origin to target misses all blocks, or only
     * strikes the exempt self-block if one is specified.
     *
     * @param level     the current level
     * @param player    the aiming player
     * @param from      ray origin
     * @param to        ray target
     * @param selfBlock block position to exempt from occlusion, or null
     * @return true if no block intersection (or only the exempt block)
     */
    private static boolean isClearRay(Level level, Player player, Vec3 from, Vec3 to,
                                      @Nullable BlockPos selfBlock) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS
                || (selfBlock != null && selfBlock.equals(hit.getBlockPos()));
    }

    /**
     * Builds 7 sample points on an AABB: center plus 6 inset face centers.
     *
     * @param box the bounding box
     * @return sample points for line-of-sight raycasts
     */
    private static Vec3[] buildLosSamples(AABB box) {
        Vec3 c = box.getCenter();
        Vec3 top = new Vec3(c.x, box.maxY - LOS_INSET, c.z);
        Vec3 bottom = new Vec3(c.x, box.minY + LOS_INSET, c.z);
        Vec3[] axis = buildAxisSamples(box, c);
        return new Vec3[]{
                c, top, bottom, axis[AXIS_WEST], axis[AXIS_EAST], axis[AXIS_NORTH], axis[AXIS_SOUTH],
        };
    }

    /**
     * Builds the 4 horizontal axis face-center samples (west, east, north, south).
     *
     * @param box    the bounding box
     * @param center the box center
     * @return array of [west, east, north, south] samples
     */
    private static Vec3[] buildAxisSamples(AABB box, Vec3 center) {
        return new Vec3[]{
                new Vec3(box.minX + LOS_INSET, center.y, center.z),
                new Vec3(box.maxX - LOS_INSET, center.y, center.z),
                new Vec3(center.x, center.y, box.minZ + LOS_INSET),
                new Vec3(center.x, center.y, box.maxZ - LOS_INSET),
        };
    }

    /**
     * Sealed aim-hit kind produced by the resolver. Packs either a living
     * entity or a block position so the caller can dispatch
     * render and throw-payload paths differently.
     */
    sealed interface AimHit {
        /**
         * The box the cone and line-of-sight tests aim at.
         *
         * @return the target's bounds
         */
        AABB box();

        /**
         * The box the crosshair's exact ray must clip.
         *
         * @return the pickable bounds, the target's bounds unless it widens them
         */
        default AABB pickBox() {
            return box();
        }

        /**
         * The block whose own voxel may not occlude the target.
         *
         * @return the target's own block, or null
         */
        default @Nullable BlockPos selfBlock() {
            return null;
        }

        /**
         * Whether the target still exists to be retained.
         *
         * @return true while the target lives
         */
        default boolean isAlive() {
            return true;
        }

        /**
         * An entity hit (one the ability takes, within cone + LOS).
         *
         * @param entity the entity aimed at
         */
        record EntityHit(Entity entity) implements AimHit {
            @Override
            public AABB box() {
                return entity.getBoundingBox();
            }

            @Override
            public AABB pickBox() {
                return entity.getBoundingBox().inflate(entity.getPickRadius());
            }

            @Override
            public boolean isAlive() {
                return entity.isAlive();
            }
        }
    }
}

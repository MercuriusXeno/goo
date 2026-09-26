package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.StackKey;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Target resolution with two-pass aim assist (exact raytrace + cone scan)
 * and sticky retention to prevent flicker at cone edges.
 *
 * <p>Handles both living entities and placed {@link ChainMarkerBlockEntity}
 * blocks in a single unified pass: chain markers behave exactly like
 * entities for the purposes of targeting, including sticky retention.
 */
final class AimAssistResolver {

    /**
     * Maximum range for blob throwing in blocks.
     */
    private static final double MAX_RANGE = AimState.MAX_RANGE;

    /**
     * Aim-assist forgiveness half-angle in degrees. Targets within this cone
     * around the reticle are eligible even if the exact raytrace misses
     * their hitbox.
     */
    private static final double AIM_ASSIST_DEGREES = 4.0;

    /**
     * Cosine of the aim-assist angle - precomputed for dot-product checks.
     */
    private static final double AIM_ASSIST_COS = Math.cos(Math.toRadians(AIM_ASSIST_DEGREES));

    /**
     * Sticky-target retention half-angle in degrees. Once locked, a target
     * stays selected until the reticle drifts beyond this wider cone,
     * preventing flicker when the cursor is near the edge.
     */
    private static final double STICKY_DEGREES = 5.0;

    /**
     * Cosine of the sticky retention angle.
     */
    private static final double STICKY_COS = Math.cos(Math.toRadians(STICKY_DEGREES));

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
     * Finds the best aim hit (living entity or chain marker block) using a
     * two-pass approach plus sticky retention on the previous hit.
     *
     * <ol>
     *   <li>Exact raytrace - if the reticle directly clips a candidate AABB,
     *       pick the nearest by distance. Entity exact-hits win over marker
     *       exact-hits when both are present at equal distance.</li>
     *   <li>Cone scan - pick the candidate with highest cosine to the
     *       reticle (entities and markers compared against the same cosine).</li>
     *   <li>Sticky - if the previous hit is still within the wider sticky
     *       cone, keep it.</li>
     * </ol>
     * <p>
     * All passes require line-of-sight (with a self-voxel exemption for
     * chain markers whose own block would otherwise occlude their AABB).
     *
     * @param player   the interacting player
     * @param from     the ray start (eye position)
     * @param to       the ray end (eye + look * range)
     * @param previous the previous frame's hit, or null
     * @param abilityId the glove's selected ability id, the only marker key it locks
     * @return the best hit, or null if none in range/cone
     */
    static @Nullable AimHit findClosestAimHit(Player player, Vec3 from, Vec3 to,
                                              @Nullable AimHit previous, @Nullable String abilityId) {
        Vec3 lookDir = to.subtract(from).normalize();
        Level level = player.level();
        List<Entity> entities = gatherCandidates(player, from, to);
        List<BlockPos> markers = gatherChainMarkers(level, from, to, abilityId);

        List<AimHit> candidates = asHits(entities, markers);
        AimHit exact = findExactHit(candidates, player, from, to);
        if (exact != null) {
            return exact;
        }

        AimHit bestCone = findBestConeHit(candidates, player, from, lookDir);

        AimHit sticky = retainSticky(candidates, player, from, lookDir, bestCone, previous);
        return (sticky != null) ? sticky : bestCone;
    }

    /**
     * Gathers living, pickable entities within the aim-assist cone's bounding volume.
     *
     * @param player the aiming player (excluded from results)
     * @param from   ray start (eye position)
     * @param to     ray end (eye + look * range)
     * @return candidate entities in the inflated search box
     */
    private static List<Entity> gatherCandidates(Player player, Vec3 from, Vec3 to) {
        AABB searchBox = buildSearchBox(player, from, to);
        return player.level().getEntities(
                player, searchBox, e -> e instanceof LivingEntity && e.isPickable());
    }

    /**
     * Gathers placed chain marker block positions within the search box by
     * iterating only the chunks that intersect it. Uses {@code getChunkNow}
     * so we never force-load chunks client-side.
     *
     * @param level the current level
     * @param from  ray start (eye position)
     * @param to    ray end (eye + look * range)
     * @param abilityId the glove's selected ability id
     * @return list of chain marker block positions inside the search box
     */
    private static List<BlockPos> gatherChainMarkers(Level level, Vec3 from, Vec3 to, @Nullable String abilityId) {
        // Reuse the same AABB as entity gathering so the cone shape is consistent.
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        AABB box = new AABB(from, to).inflate(coneRadius + 1.0);
        int minCX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
        int maxCX = SectionPos.blockToSectionCoord((int) Math.floor(box.maxX));
        int minCZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
        int maxCZ = SectionPos.blockToSectionCoord((int) Math.floor(box.maxZ));
        List<BlockPos> markers = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                collectMarkersInChunk(level, cx, cz, box, abilityId, markers);
            }
        }
        return markers;
    }

    /**
     * Whether the aim assist locks a standing marker for the glove's selection.
     *
     * @param markerAbilityId   the marker's ability id
     * @param selectedAbilityId the glove's selected ability id, or null
     * @return true only when the marker runs the selected ability
     */
    static boolean locksMarker(@Nullable String markerAbilityId, @Nullable String selectedAbilityId) {
        return StackKey.matches(markerAbilityId, selectedAbilityId);
    }

    /**
     * Pulls the selected ability's chain marker block entities out of a
     * single chunk if it is currently loaded client-side.
     *
     * @param level     the current level
     * @param cx        chunk X coordinate
     * @param cz        chunk Z coordinate
     * @param box       the search bounding box in world space
     * @param abilityId the glove's selected ability id
     * @param out       list to append matching positions to
     */
    private static void collectMarkersInChunk(Level level, int cx, int cz,
                                              AABB box, @Nullable String abilityId, List<BlockPos> out) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) {
            return;
        }
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof ChainMarkerBlockEntity marker && locksMarker(marker.getAbilityId(), abilityId))) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            if (box.contains(Vec3.atCenterOf(pos))) {
                out.add(pos.immutable());
            }
        }
    }

    /**
     * Builds the shared search bounding box used for both entity and marker
     * candidate gathering.
     *
     * @param player the aiming player
     * @param from   ray start
     * @param to     ray end
     * @return the inflated search box
     */
    private static AABB buildSearchBox(Player player, Vec3 from, Vec3 to) {
        double coneRadius = MAX_RANGE * Math.tan(Math.toRadians(AIM_ASSIST_DEGREES));
        return player.getBoundingBox()
                .expandTowards(to.subtract(from))
                .inflate(coneRadius + 1.0);
    }

    /**
     * Every candidate as an aim hit, entities first so an entity wins a tie
     * against a marker.
     *
     * @param entities entity candidates
     * @param markers  chain marker candidates
     * @return the candidates in tie-break order
     */
    private static List<AimHit> asHits(List<Entity> entities, List<BlockPos> markers) {
        List<AimHit> hits = new ArrayList<>(entities.size() + markers.size());
        for (Entity entity : entities) {
            hits.add(new AimHit.EntityHit(entity));
        }
        for (BlockPos pos : markers) {
            hits.add(new AimHit.ChainMarkerHit(pos));
        }
        return hits;
    }

    /**
     * Finds the nearest candidate the reticle's ray clips, in sight.
     *
     * @param candidates the candidates in tie-break order
     * @param player     the aiming player
     * @param from       ray start
     * @param to         ray end
     * @return the closest exact hit, or null
     */
    private static @Nullable AimHit findExactHit(List<AimHit> candidates, Player player, Vec3 from, Vec3 to) {
        double bestDist = Double.MAX_VALUE;
        AimHit best = null;
        for (AimHit candidate : candidates) {
            Optional<Vec3> clip = candidate.pickBox().clip(from, to);
            if (clip.isEmpty()) {
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
     * Finds the candidate closest to the reticle inside the aim-assist cone.
     *
     * @param candidates the candidates in tie-break order
     * @param player     the aiming player
     * @param from       ray start
     * @param lookDir    normalized look direction
     * @return the cone-pass winner, or null if nothing clears the cone
     */
    private static @Nullable AimHit findBestConeHit(List<AimHit> candidates, Player player,
                                                    Vec3 from, Vec3 lookDir) {
        double bestCos = AIM_ASSIST_COS;
        AimHit best = null;
        for (AimHit candidate : candidates) {
            double cos = coneCosine(candidate, player, from, lookDir, AIM_ASSIST_COS);
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
     * @param player     the aiming player
     * @param from       ray start
     * @param lookDir    normalized look direction
     * @param bestCone   the cone-pass winner this frame
     * @param previous   the previous hit, or null
     * @return the previous hit if it is retained, or null
     */
    private static @Nullable AimHit retainSticky(List<AimHit> candidates, Player player, Vec3 from,
                                                 Vec3 lookDir, @Nullable AimHit bestCone,
                                                 @Nullable AimHit previous) {
        if (previous == null || previous.equals(bestCone)
                || !previous.isAlive() || !candidates.contains(previous)) {
            return null;
        }
        return coneCosine(previous, player, from, lookDir, STICKY_COS) > STICKY_COS ? previous : null;
    }

    /**
     * The cosine between the look direction and the candidate's center when
     * the candidate is in range, inside the given cone and in sight.
     *
     * @param candidate the candidate
     * @param player    the aiming player
     * @param from      ray start
     * @param lookDir   normalized look direction
     * @param coneCos   the cosine of the cone's half-angle
     * @return the cosine, or NO_CONE_ANGLE when out of range, outside the cone or occluded
     */
    private static double coneCosine(AimHit candidate, Player player, Vec3 from, Vec3 lookDir, double coneCos) {
        Vec3 toTarget = candidate.box().getCenter().subtract(from);
        double dist = toTarget.length();
        if (dist <= CENTER_HALF || dist > MAX_RANGE) {
            return NO_CONE_ANGLE;
        }
        double cos = lookDir.dot(toTarget.normalize());
        return cos > coneCos && isInSight(candidate, player, from) ? cos : NO_CONE_ANGLE;
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
     * (prevents a chain marker's own voxel from occluding its own LOS).
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
     * entity or a chain marker block position so the caller can dispatch
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
         * The box the reticle's exact ray must clip.
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
         * An entity hit (living, pickable, within cone + LOS).
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

        /**
         * A chain marker block hit, behaving like an entity for targeting.
         *
         * @param pos the marker's block
         */
        record ChainMarkerHit(BlockPos pos) implements AimHit {
            @Override
            public AABB box() {
                return new AABB(pos);
            }

            @Override
            public BlockPos selfBlock() {
                return pos;
            }
        }
    }
}

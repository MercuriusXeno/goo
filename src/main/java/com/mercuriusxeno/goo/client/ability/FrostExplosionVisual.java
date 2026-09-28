package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.AreaShape;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Frost goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a rolling freeze fog that fills the zone the
 * marker freezes. Cold rime fog rolls out from the marker through the zone's
 * exact shape over 20 ticks on an ease-out: billowing white-blue noise, soft
 * and matte with no facets, and a crisp frost-white leading edge where the
 * fog front meets air, so the freeze is seen to travel
 * ({@code frost_explosion.fsh}). It then settles into hanging mist that fades
 * over 10 more ticks, alpha blended. The zone is the one the ability's
 * progressive-area step freezes: for a sphere, a sphere of the freeze radius
 * centered one block into the wall; for a tunnel or flat circle, the
 * footprint's box. The vertex color carries progress in red, how far the fog
 * has rolled in green and how much mist is left in blue, since a core
 * pipeline takes no per-draw uniforms.
 */
public final class FrostExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final FrostExplosionVisual INSTANCE = new FrostExplosionVisual();

    /** Ticks the fog takes to roll out through the zone. */
    static final int ROLL_TICKS = 20;
    /** Ticks the settled mist takes to fade. */
    static final int MIST_TICKS = 10;
    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = ROLL_TICKS + MIST_TICKS;
    private static final int OPAQUE = 0xFF;
    private static final float HALF = 0.5f;

    /**
     * The zone the fog fills, in block-local coordinates of the marker.
     *
     * @param centerX the zone's center X
     * @param centerY the zone's center Y
     * @param centerZ the zone's center Z
     * @param halfX   the zone's half-extent on X
     * @param halfY   the zone's half-extent on Y
     * @param halfZ   the zone's half-extent on Z
     * @param boxy    true for a box, false for a sphere
     */
    record FrostZone(float centerX, float centerY, float centerZ, float halfX, float halfY, float halfZ,
                     boolean boxy) {
    }

    private FrostExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.FROST;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float rolled = rolled(progress);
        AreaShape shape = SyncedSteps.first(burnout.abilityId(), ProgressiveAreaStep.class)
                .map(ProgressiveAreaStep::shape).orElse(AreaShape.SPHERE);
        FrostZone zone = zone(shape, burnout.stackCount(), burnout.placedFace());
        int color = ARGB.color(OPAQUE, NetherDiscMesh.toByte(progress), NetherDiscMesh.toByte(rolled),
                NetherDiscMesh.toByte(mist(progress)));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.FROST_EXPLOSION_TYPE, (pose, c) ->
                emitZone(pose, c, zone, rolled, color));
    }

    /**
     * How far the fog has rolled through the zone: an ease-out over ROLL_TICKS.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the roll in [0, 1]
     */
    static float rolled(float progress) {
        return BurnoutGeometry.easeOutCubic(Math.min(1f, progress * DURATION_TICKS / ROLL_TICKS));
    }

    /**
     * How much mist is left: whole through the roll, fading over MIST_TICKS.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the mist's opacity in [0, 1]
     */
    static float mist(float progress) {
        return BurnoutGeometry.fadeAfter(progress, (float) ROLL_TICKS / DURATION_TICKS);
    }

    /**
     * The zone a frost marker freezes, in block-local coordinates: a sphere of
     * the freeze radius centered one block into the wall, or the tunnel's or
     * flat circle's footprint box.
     *
     * @param shape  the progressive-area step's shape
     * @param stacks the marker's stack count
     * @param face   the placed face
     * @return the zone
     */
    static FrostZone zone(AreaShape shape, int stacks, Direction face) {
        if (shape == AreaShape.SPHERE) {
            float radius = AbilityMath.computeFreezeRadius(stacks);
            Direction into = face.getOpposite();
            return new FrostZone(HALF + into.getStepX(), HALF + into.getStepY(), HALF + into.getStepZ(),
                    radius, radius, radius, false);
        }
        AABB box = ChainFootprint.computeBounds(stacks, shape == AreaShape.FLAT_CIRCLE, face);
        Vector3f center = box.getCenter().toVector3f();
        return new FrostZone(center.x(), center.y(), center.z(), (float) box.getXsize() * HALF,
                (float) box.getYsize() * HALF, (float) box.getZsize() * HALF, true);
    }

    /**
     * Emits the fog's front: the zone's surface grown from the marker's center
     * by the roll, the unit sphere mesh pushed out onto a box's faces for a
     * boxy zone, each vertex's normal its sphere direction.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param zone   the zone
     * @param rolled how far the fog has rolled
     * @param color  the packed color
     */
    private static void emitZone(PoseStack.Pose pose, VertexConsumer c, FrostZone zone, float rolled, int color) {
        float cx = BurnoutGeometry.BLOCK_CENTER + (zone.centerX() - BurnoutGeometry.BLOCK_CENTER) * rolled;
        float cy = BurnoutGeometry.BLOCK_CENTER + (zone.centerY() - BurnoutGeometry.BLOCK_CENTER) * rolled;
        float cz = BurnoutGeometry.BLOCK_CENTER + (zone.centerZ() - BurnoutGeometry.BLOCK_CENTER) * rolled;
        FlatQuadContext quads = new FlatQuadContext(pose, c);
        for (Vector3f v : NetherSphereVisual.unitSphereMesh()) {
            float push = zone.boxy() ? 1f / Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z()))) : 1f;
            quads.vertex(cx + v.x() * push * zone.halfX() * rolled, cy + v.y() * push * zone.halfY() * rolled,
                    cz + v.z() * push * zone.halfZ() * rolled, color, v.x(), v.y(), v.z());
        }
    }
}

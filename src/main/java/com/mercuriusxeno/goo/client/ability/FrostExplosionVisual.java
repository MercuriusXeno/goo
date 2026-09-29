package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.ability.program.AreaShape;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frost goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a fog ring with snowflakes. Built like
 * rock's dust shock disc, a flat ring in the placed face's plane spreads out
 * from the marker to the freeze zone's reach over 20 ticks on an ease-out:
 * for a sphere, the freeze radius; for a tunnel or flat circle, the
 * footprint's widest reach across the face. Its fragment shader
 * ({@code frost_explosion.fsh}) draws it with frost's fog, white-blue
 * billows with a crisp frost-white leading edge, the fog thinning behind the
 * edge, fading over 10 more ticks, alpha blended. At burnout a burst of
 * vanilla snowflake particles scatters outward across the ring's plane,
 * riding out with the edge and drifting down. The vertex color carries
 * progress in red, the disc-local position in green and blue, and the fog's
 * remaining opacity in alpha, since a core pipeline takes no per-draw
 * uniforms.
 */
public final class FrostExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final FrostExplosionVisual INSTANCE = new FrostExplosionVisual();

    /** Ticks the ring takes to spread to the zone's reach. */
    static final int SPREAD_TICKS = 20;
    /** Ticks the fog takes to fade once the ring has spread. */
    static final int FADE_TICKS = 10;
    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = SPREAD_TICKS + FADE_TICKS;
    /** How many snowflakes the burst scatters. */
    static final int SNOWFLAKES = 48;
    /** Snowflake speed as a share of the ring's reach per tick, so the burst rides out with the edge. */
    static final float SNOWFLAKE_SPEED = 0.08f;
    /** How far the ring sits from the block center along the face's step: just off the face plane. */
    private static final float RING_LIFT = -0.47f;
    private static final int RING_SEGMENTS = 48;
    /** Maps a disc-local coordinate in [-1, 1] onto [0, 1] for a color byte. */
    private static final float SIGNED_TO_UNIT = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;

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
    public void begin(ChainBurnouts.Burnout burnout, ClientLevel level) {
        Direction face = burnout.placedFace();
        float reach = zoneReach(areaShape(burnout), burnout.stackCount(), face);
        BlockPos pos = burnout.pos();
        double x = pos.getX() + BurnoutGeometry.BLOCK_CENTER + face.getStepX() * RING_LIFT;
        double y = pos.getY() + BurnoutGeometry.BLOCK_CENTER + face.getStepY() * RING_LIFT;
        double z = pos.getZ() + BurnoutGeometry.BLOCK_CENTER + face.getStepZ() * RING_LIFT;
        for (int i = 0; i < SNOWFLAKES; i++) {
            Vec3 velocity = snowflakeVelocity(face, TWO_PI * (i + level.getRandom().nextFloat()) / SNOWFLAKES,
                    reach * SNOWFLAKE_SPEED * (1f - SIGNED_TO_UNIT * level.getRandom().nextFloat()));
            level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, velocity.x, velocity.y, velocity.z);
        }
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = zoneReach(areaShape(burnout), burnout.stackCount(), burnout.placedFace()) * spread(progress);
        int progressByte = NetherDiscMesh.toByte(progress);
        int fog = NetherDiscMesh.toByte(fog(progress));
        int center = ARGB.color(fog, progressByte, NetherDiscMesh.toByte(SIGNED_TO_UNIT),
                NetherDiscMesh.toByte(SIGNED_TO_UNIT));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.FROST_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitAnnulus(pose, c, burnout.placedFace(), RING_LIFT, 0f, radius, RING_SEGMENTS,
                        (angle, outer) -> outer ? edgeColor(fog, progressByte, angle) : center));
    }

    /**
     * How far the ring has spread toward the zone's reach: an ease-out over SPREAD_TICKS.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the spread in [0, 1]
     */
    static float spread(float progress) {
        return BurnoutGeometry.easeOutCubic(Math.min(1f, progress * DURATION_TICKS / SPREAD_TICKS));
    }

    /**
     * How much fog is left: whole while the ring spreads, fading over FADE_TICKS.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the fog's opacity in [0, 1]
     */
    static float fog(float progress) {
        return BurnoutGeometry.fadeAfter(progress, (float) SPREAD_TICKS / DURATION_TICKS);
    }

    /**
     * How far the zone a frost marker freezes reaches across its face: the
     * freeze radius for a sphere, the footprint's widest reach from the
     * marker's center across the face for a tunnel or flat circle.
     *
     * @param shape  the progressive-area step's shape
     * @param stacks the marker's stack count
     * @param face   the placed face
     * @return the reach in blocks
     */
    static float zoneReach(AreaShape shape, int stacks, Direction face) {
        if (shape == AreaShape.SPHERE) {
            return AbilityMath.computeFreezeRadius(stacks);
        }
        AABB box = ChainFootprint.computeBounds(stacks, shape == AreaShape.FLAT_CIRCLE, face);
        double center = BurnoutGeometry.BLOCK_CENTER;
        double reach = 0;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != face.getAxis()) {
                reach = Math.max(reach, Math.max(center - box.min(axis), box.max(axis) - center));
            }
        }
        return (float) reach;
    }

    /**
     * A snowflake's launch velocity: outward across the ring's plane at the given angle.
     *
     * @param face  the placed face
     * @param angle the angle about the face axis, in radians
     * @param speed the speed in blocks per tick
     * @return the velocity
     */
    static Vec3 snowflakeVelocity(Direction face, double angle, float speed) {
        double u = Math.cos(angle) * speed;
        double v = Math.sin(angle) * speed;
        return switch (face.getAxis()) {
            case X -> new Vec3(0, u, v);
            case Y -> new Vec3(u, 0, v);
            case Z -> new Vec3(u, v, 0);
        };
    }

    /**
     * The shape of the zone the burnout's ability freezes, read off its
     * synced progressive-area step.
     *
     * @param burnout the burnout
     * @return the zone's shape, a sphere when the step cannot be read
     */
    private static AreaShape areaShape(ChainBurnouts.Burnout burnout) {
        return SyncedSteps.first(burnout.abilityId(), ProgressiveAreaStep.class)
                .map(ProgressiveAreaStep::shape).orElse(AreaShape.SPHERE);
    }

    /**
     * The color of a vertex on the ring's edge: fog, progress, and its disc-local position.
     *
     * @param fog          the fog's opacity as a byte
     * @param progressByte the explosion's progress as a byte
     * @param angle        the vertex's angle about the face axis
     * @return the packed color
     */
    private static int edgeColor(int fog, int progressByte, double angle) {
        int u = NetherDiscMesh.toByte(((float) Math.cos(angle) + 1f) * SIGNED_TO_UNIT);
        int v = NetherDiscMesh.toByte(((float) Math.sin(angle) + 1f) * SIGNED_TO_UNIT);
        return ARGB.color(fog, progressByte, u, v);
    }
}

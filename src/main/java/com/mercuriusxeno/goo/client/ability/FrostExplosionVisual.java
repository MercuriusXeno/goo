package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.List;

/**
 * Frost goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a fog ring with snowflakes. Built like
 * rock's dust shock disc, a flat ring in the placed face's plane spreads out
 * from the marker to the freeze zone's reach over 20 ticks on an ease-out.
 * Its fragment shader
 * ({@code frost_explosion.fsh}) draws it with frost's fog, white-blue
 * billows with a crisp frost-white leading edge, the fog thinning behind the
 * edge, fading over 10 more ticks, alpha blended. At burnout a burst of
 * vanilla snowflake particles scatters outward across the ring's plane,
 * riding out with the edge and drifting down. The vertex color carries
 * progress in red, the disc-local position in green and blue, and the fog's
 * remaining opacity in alpha, since a core pipeline takes no per-draw
 * uniforms.
 */
public final class FrostExplosionVisual implements BurnoutVisual, HeldGhostVisual {

    /** The one instance the burnout registry holds. */
    public static final FrostExplosionVisual INSTANCE = new FrostExplosionVisual();

    /** How far the zone a frost marker freezes reaches across its face, in blocks. */
    static final float ZONE_REACH = 1f;
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
    /** The progress the held ghost rests at: the ring fully spread, its fog whole. */
    private static final float HELD_PROGRESS = (float) SPREAD_TICKS / DURATION_TICKS;
    /** How far the ring sits from the block center along the face's step: just off the face plane. */
    private static final float RING_LIFT = -0.47f;
    private static final int RING_SEGMENTS = 48;
    /** Maps a disc-local coordinate in [-1, 1] onto [0, 1] for a color byte. */
    private static final float SIGNED_TO_UNIT = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Mixes a ring's fixed value before hashing, so neighbouring rings seed far apart. */
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    /** Spreads a hashed value across the unit before it becomes an angle. */
    private static final double SEED_SCALE = 1.0 / 4096;

    private FrostExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.FROST;
    }

    /**
     * The Orb's held ghost: frost's fog ring lying whole on the face the
     * throw strikes, out to the reach of its landing freeze, so the player
     * sees what the ball freezes outright where it lands
     * (decisions orb-carries-a-swirling-nova, held-visual-ghosts-the-landing-in-two-passes).
     *
     * @return the ghost's one layer
     */
    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.FROST_EXPLOSION_TYPE,
                GooRenderTypes.FROST_EXPLOSION_THROUGH_BLOCKS_TYPE, FrostExplosionVisual::emitHeld));
    }

    private static void emitHeld(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                                 float opacity, double nowSeconds) {
        emitWholeFog(pose, c, face, RING_LIFT, ghost.domeRadius(), opacity);
    }

    /**
     * Emits frost's fog disc fully spread and whole, square to a face: the
     * Orb's held ghost lies this way on the struck face
     * (decision orb-carries-a-swirling-nova).
     *
     * @param pose    the pose entry
     * @param c       the vertex consumer
     * @param face    the face the disc lies square to
     * @param lift    the shift from the block center along the face's step
     * @param reach   the disc's radius in blocks
     * @param opacity the share of the fog's opacity
     */
    static void emitWholeFog(PoseStack.Pose pose, VertexConsumer c, Direction face, float lift, float reach,
                                    float opacity) {
        int progressByte = NetherDiscMesh.toByte(HELD_PROGRESS);
        int fog = NetherDiscMesh.toByte(opacity);
        int center = ARGB.color(fog, progressByte, NetherDiscMesh.toByte(SIGNED_TO_UNIT),
                NetherDiscMesh.toByte(SIGNED_TO_UNIT));
        BurnoutGeometry.emitAnnulus(pose, c, face, lift, 0f, reach, RING_SEGMENTS,
                (angle, outer) -> outer ? edgeColor(fog, progressByte, angle) : center);
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void begin(ChainBurnouts.Burnout burnout, ClientLevel level) {
        Direction face = burnout.placedFace();
        float reach = ZONE_REACH;
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
        drawRing(frame, Vec3.atLowerCornerOf(burnout.pos()), burnout.placedFace(), RING_LIFT, ZONE_REACH,
                burnout.progress(frame.gameTime()), seedOf(burnout.pos().asLong() ^ burnout.startTick()));
    }

    /**
     * A ring's seed from a value fixed for the ring's life, so its fog holds
     * one look from frame to frame and differs from every other ring's.
     *
     * @param value the ring's fixed value, such as its position and start
     * @return the seed, an angle in radians
     */
    static float seedOf(long value) {
        return (float) (Mth.frac(Long.hashCode(value * SEED_MIX) * SEED_SCALE) * TWO_PI);
    }

    /**
     * The normal that carries a ring's seed to the frost fog shader, which
     * reads it back as the angle of the normal about the up axis.
     * decision nova-ring-grows-with-the-hold
     *
     * @param seed the seed, an angle in radians
     * @return the unit normal
     */
    static Vector3f seedNormal(float seed) {
        return new Vector3f(Mth.cos(seed), 0f, Mth.sin(seed));
    }

    /**
     * Draws the frost ring spread toward a reach, its block-local center a
     * block's center shifted along the face by lift; Nova draws its ring
     * this way at the caster's feet (decision nova-ring-grows-with-the-hold).
     *
     * @param frame    the frame being drawn
     * @param corner   the world point the ring's block-local coordinates are measured from
     * @param face     the face the ring lies square to
     * @param lift     the shift from the block center along the face's step
     * @param reach    the reach the ring spreads to, in blocks
     * @param progress the ring's progress in [0, 1]
     * @param seed     the ring's seed, which its fog billows from
     */
    static void drawRing(BurnoutFrame frame, Vec3 corner, Direction face, float lift, float reach, float progress,
                         float seed) {
        drawDisc(frame, corner, face, lift, reach * spread(progress), progress, fog(progress), seed);
    }

    /**
     * Draws frost's fog disc at a radius and fog given outright, for a ring
     * paced on its own clock, such as Nova's fast ring
     * (decision nova-ring-grows-with-the-hold).
     *
     * @param frame    the frame being drawn
     * @param corner   the world point the disc's block-local coordinates are measured from
     * @param face     the face the disc lies square to
     * @param lift     the shift from the block center along the face's step
     * @param radius   the disc's radius in blocks
     * @param progress the share of its life the disc has lived, which drifts its billows
     * @param fogShare the fog's opacity, 0 to 1
     * @param seed     the ring's seed, which its fog billows from
     */
    static void drawDisc(BurnoutFrame frame, Vec3 corner, Direction face, float lift, float radius, float progress,
                         float fogShare, float seed) {
        int progressByte = NetherDiscMesh.toByte(progress);
        int fog = NetherDiscMesh.toByte(fogShare);
        int center = ARGB.color(fog, progressByte, NetherDiscMesh.toByte(SIGNED_TO_UNIT),
                NetherDiscMesh.toByte(SIGNED_TO_UNIT));
        BurnoutGeometry.drawAt(frame, corner, GooRenderTypes.FROST_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitAnnulus(pose, c, face, lift, 0f, radius, RING_SEGMENTS,
                        (angle, outer) -> outer ? edgeColor(fog, progressByte, angle) : center, seedNormal(seed)));
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

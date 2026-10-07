package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Metal goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): an urchin, a quick show of force that
 * shows the trap is primed. About 24 short cone spikes, the cone shape the
 * trap stabs with, spread evenly over the outward half of the marker (none
 * into the wall), snap out to 1 block in 3 ticks, hold bristling for 5
 * ticks, then retract over 6, 14 ticks in all. The fragment shader
 * ({@code metal_explosion.fsh}) shades each cone chrome, C0C0C0 to
 * E8E8E8, with a specular band sweeping down the spikes and a white glint
 * at each tip. The vertex color carries progress in red and the vertex's
 * place along its spike in green, since a core pipeline takes no per-draw
 * uniforms.
 */
public final class MetalExplosionVisual implements BurnoutVisual, HeldGhostVisual {

    /** The one instance the burnout registry holds. */
    public static final MetalExplosionVisual INSTANCE = new MetalExplosionVisual();

    /** Ticks the spikes take to snap out. */
    static final int ARM_TICKS = 3;
    /** Ticks the spikes hold bristling. */
    static final int HOLD_TICKS = 5;
    /** Ticks the spikes take to retract. */
    static final int RETRACT_TICKS = 6;
    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = ARM_TICKS + HOLD_TICKS + RETRACT_TICKS;
    /** A spike's full length in blocks. */
    static final float SPIKE_REACH = 1f;
    /** How many directions the sphere of candidates holds; the outward half of them become spikes. */
    static final int CANDIDATE_DIRECTIONS = 48;
    /** A spike must point at least this far out of the wall, as the cosine to the face's step. */
    static final float OUTWARD_MIN = 0.1f;

    /** Real-time seconds the held ghost's specular band takes over one show's sweeps. */
    static final double HELD_SWEEP_SECONDS = 2.0;

    private static final float SPIKE_BASE_RADIUS = 0.06f;
    private static final int SPIKE_SIDES = 4;
    /** How far the spikes' common base sits from the block center along the face's step: just off the face plane. */
    private static final float BASE_LIFT = -0.4f;
    private static final int OPAQUE = 0xFF;
    /** The golden angle in radians, pi * (3 - sqrt 5), which spreads a spiral's points evenly. */
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final double TWO_PI = 2 * Math.PI;
    /** Half, for a side's mid angle and a spiral point's centered index. */
    private static final float HALF = 0.5f;
    private static final List<Vector3f> CANDIDATES = fibonacciSphere(CANDIDATE_DIRECTIONS);

    private MetalExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.METAL;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float length = spikeLength(progress);
        if (length <= 0f) {
            return;
        }
        Urchin urchin = new Urchin(burnout.placedFace(), length, SPIKE_BASE_RADIUS, NetherDiscMesh.toByte(progress),
                OPAQUE);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.METAL_EXPLOSION_TYPE, urchin::emit);
    }

    @Override
    public RenderType heldType() {
        return GooRenderTypes.METAL_EXPLOSION_TYPE;
    }

    @Override
    public RenderType heldThroughBlocksType() {
        return GooRenderTypes.METAL_EXPLOSION_THROUGH_BLOCKS_TYPE;
    }

    /**
     * Metal's ghost: the urchin at its hold, every spike out to the dome's
     * radius and as thick for its length as a landing's, the specular band
     * still sweeping on the real-time clock.
     * held-visual-ghosts-the-landing-in-two-passes
     */
    @Override
    public void emitHeld(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face, float opacity,
                         double nowSeconds) {
        heldUrchin(ghost, face, opacity, nowSeconds).emit(pose, c);
    }

    /**
     * The urchin a held ghost draws: spikes the dome's radius long, their
     * base scaled with them, the band's place on the real-time clock.
     *
     * @param ghost      the ghost
     * @param face       the face the throw strikes
     * @param opacity    the share of the landing's opacity
     * @param nowSeconds seconds on the real-time clock
     * @return the urchin
     */
    static Urchin heldUrchin(HeldGhost ghost, Direction face, float opacity, double nowSeconds) {
        float length = ghost.domeRadius();
        float sweep = (float) Mth.frac(nowSeconds / HELD_SWEEP_SECONDS);
        return new Urchin(face, length, SPIKE_BASE_RADIUS * length / SPIKE_REACH, NetherDiscMesh.toByte(sweep),
                NetherDiscMesh.toByte(opacity));
    }

    /**
     * One urchin's spikes over a placed face.
     *
     * @param face         the placed face
     * @param length       each spike's length in blocks
     * @param baseRadius   each spike's base radius in blocks
     * @param progressByte the show's progress as a byte, where the specular band sits
     * @param alpha        the spikes' opacity as a byte
     */
    record Urchin(Direction face, float length, float baseRadius, int progressByte, int alpha) {
        /**
         * Emits every spike pointing out of the wall.
         *
         * @param pose the pose entry
         * @param c    the vertex consumer
         */
        void emit(PoseStack.Pose pose, VertexConsumer c) {
            for (Vector3f dir : spikeDirections(face)) {
                emitSpike(pose, c, this, dir);
            }
        }
    }

    /**
     * A spike's length through the show: snapping out on an ease-out over
     * ARM_TICKS, holding over HOLD_TICKS, retracting over RETRACT_TICKS.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the spike's length in blocks
     */
    static float spikeLength(float progress) {
        float tick = progress * DURATION_TICKS;
        if (tick < ARM_TICKS) {
            return SPIKE_REACH * BurnoutGeometry.easeOutCubic(tick / ARM_TICKS);
        }
        if (tick < ARM_TICKS + HOLD_TICKS) {
            return SPIKE_REACH;
        }
        float retracted = (tick - ARM_TICKS - HOLD_TICKS) / RETRACT_TICKS;
        return SPIKE_REACH * Math.max(0f, 1f - BurnoutGeometry.easeOutCubic(Math.min(1f, retracted)));
    }

    /**
     * The directions the spikes point for a marker on a face: the evenly
     * spread candidates that point out of the wall.
     *
     * @param face the placed face
     * @return the unit spike directions
     */
    static List<Vector3f> spikeDirections(Direction face) {
        List<Vector3f> outward = new ArrayList<>();
        for (Vector3f dir : CANDIDATES) {
            float out = dir.x() * face.getStepX() + dir.y() * face.getStepY() + dir.z() * face.getStepZ();
            if (out >= OUTWARD_MIN) {
                outward.add(dir);
            }
        }
        return outward;
    }

    /**
     * Emits one spike: the trap's cone from the common base along dir,
     * each vertex's green its place along the spike.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param urchin the urchin the spike belongs to
     * @param dir    the spike's unit direction
     */
    private static void emitSpike(PoseStack.Pose pose, VertexConsumer c, Urchin urchin, Vector3f dir) {
        float center = BurnoutGeometry.BLOCK_CENTER;
        Direction face = urchin.face();
        ConeGeometry.Cone cone = new ConeGeometry.Cone(
                center + face.getStepX() * BASE_LIFT, center + face.getStepY() * BASE_LIFT,
                center + face.getStepZ() * BASE_LIFT, dir.x(), dir.y(), dir.z(), urchin.length(),
                urchin.baseRadius());
        float[] basis = ConeGeometry.computeBasis(dir.x(), dir.y(), dir.z());
        int base = ARGB.color(urchin.alpha(), urchin.progressByte(), 0, 0);
        int tip = ARGB.color(urchin.alpha(), urchin.progressByte(), OPAQUE, 0);
        FlatQuadContext quads = new FlatQuadContext(pose, c);
        for (int side = 0; side < SPIKE_SIDES; side++) {
            emitSpikeSide(quads, cone, basis, side, base, tip);
        }
    }

    /**
     * Emits one triangular side of a spike, the base corners in the base
     * color and the tip in the tip color.
     *
     * @param quads the quad emitter
     * @param cone  the spike's cone
     * @param basis the cone's orthonormal basis
     * @param side  which side, counted around the cone
     * @param base  the packed color at the base
     * @param tip   the packed color at the tip
     */
    private static void emitSpikeSide(FlatQuadContext quads, ConeGeometry.Cone cone, float[] basis, int side,
                                      int base, int tip) {
        float a0 = (float) (TWO_PI * side / SPIKE_SIDES);
        float a1 = (float) (TWO_PI * (side + 1) / SPIKE_SIDES);
        float[] normal = ConeGeometry.segmentNormal(basis, (a0 + a1) * HALF);
        float nx = normal[ConeGeometry.PERP_X];
        float ny = normal[ConeGeometry.PERP_Y];
        float nz = normal[ConeGeometry.PERP_Z];
        Vector3f edge0 = ConeGeometry.baseCorner(cone, basis, a0);
        Vector3f edge1 = ConeGeometry.baseCorner(cone, basis, a1);
        ConeGeometry.emitTriangle(corner -> {
            switch (corner) {
                case ConeGeometry.BASE_START -> quads.vertex(edge0.x, edge0.y, edge0.z, base, nx, ny, nz);
                case ConeGeometry.BASE_END -> quads.vertex(edge1.x, edge1.y, edge1.z, base, nx, ny, nz);
                default -> quads.vertex(cone.baseX() + cone.dirX() * cone.length(),
                        cone.baseY() + cone.dirY() * cone.length(), cone.baseZ() + cone.dirZ() * cone.length(),
                        tip, cone.dirX(), cone.dirY(), cone.dirZ());
            }
        });
    }

    /**
     * Spreads count unit directions evenly over the sphere on the golden-angle spiral.
     *
     * @param count how many directions
     * @return the unit directions
     */
    private static List<Vector3f> fibonacciSphere(int count) {
        List<Vector3f> dirs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float spread = (i + HALF) / count;
            float y = 1f - spread - spread;
            float ring = (float) Math.sqrt(1f - y * y);
            double theta = GOLDEN_ANGLE * i;
            dirs.add(new Vector3f((float) Math.cos(theta) * ring, y, (float) Math.sin(theta) * ring));
        }
        return List.copyOf(dirs);
    }
}

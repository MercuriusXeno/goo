package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import java.util.OptionalDouble;

/**
 * Unstable goo's burnout explosion, the design the operator settled
 * (decision elemental-explosion-per-type): a neon fireball and ring. A
 * sphere at the marker's center grows on an ease-out to the blast radius,
 * the explode step's power, over 16 ticks. Its fragment shader
 * ({@code unstable_explosion.fsh}) draws a white-green core fading to a
 * 39FF14 rim, with animated noise that makes the surface crackle and
 * flicker, the goo's instability. A thin additive shockwave ring runs out
 * ahead of the sphere in the placed face's plane. Both fade to nothing by
 * the end. Progress reaches the shader through the vertex color, since a
 * core pipeline takes no per-draw uniforms: red carries progress, green
 * marks the ring and blue carries the ring's radial position.
 */
public final class UnstableExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final UnstableExplosionVisual INSTANCE = new UnstableExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 16;
    /** How far past the sphere the shockwave ring reaches, as a multiple of the blast radius. */
    static final float RING_REACH = 1.35f;
    /** The ring band's inner edge, as a fraction of the ring's radius. */
    static final float RING_INNER = 0.85f;
    /** The blast radius drawn when the ability's explode step cannot be read. */
    static final float FALLBACK_REACH = 2f;

    private static final float BLOCK_CENTER = 0.5f;
    private static final int OPAQUE = 0xFF;
    private static final int RING_SEGMENTS = 48;
    private static final double TWO_PI = 2 * Math.PI;

    private UnstableExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.UNSTABLE;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float reach = blastReach(burnout.abilityId(), burnout.stackCount());
        float sphere = sphereRadius(progress, reach);
        float ring = ringRadius(progress, reach);
        int progressByte = NetherDiscMesh.toByte(progress);

        PoseStack poseStack = frame.poseStack();
        BlockPos pos = burnout.pos();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - frame.camera().x, pos.getY() - frame.camera().y,
                pos.getZ() - frame.camera().z);
        VertexConsumer c = frame.buffers().getBuffer(GooRenderTypes.UNSTABLE_EXPLOSION_TYPE);
        emitSphere(poseStack.last(), c, sphere, ARGB.color(OPAQUE, progressByte, 0, 0));
        emitRing(poseStack.last(), c, ring, burnout.placedFace(), progressByte);
        poseStack.popPose();
        frame.buffers().endBatch(GooRenderTypes.UNSTABLE_EXPLOSION_TYPE);
    }

    /**
     * The fireball's radius: an ease-out growth to the blast radius.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param reach    the blast radius in blocks
     * @return the sphere's radius in blocks
     */
    static float sphereRadius(float progress, float reach) {
        return reach * easeOutCubic(progress);
    }

    /**
     * The shockwave ring's radius, running out ahead of the sphere.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param reach    the blast radius in blocks
     * @return the ring's outer radius in blocks
     */
    static float ringRadius(float progress, float reach) {
        return sphereRadius(progress, reach) * RING_REACH;
    }

    /**
     * The blast radius: the power of the ability's explode step at the
     * marker's stack count, read off the synced ability.
     *
     * @param abilityId  the ability the marker ran
     * @param stackCount the marker's stack count at burnout
     * @return the blast radius in blocks
     */
    private static float blastReach(String abilityId, int stackCount) {
        Variables stacks = name -> HostVariables.STACKS.equals(name)
                ? OptionalDouble.of(stackCount) : OptionalDouble.empty();
        return SyncedSteps.first(abilityId, ExplodeStep.class)
                .map(step -> step.power().evaluateFloat(stacks))
                .orElse(FALLBACK_REACH);
    }

    /**
     * Emits the fireball: the unit sphere scaled to radius about the block center.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param radius the sphere's radius in blocks
     * @param color  the packed progress color
     */
    private static void emitSphere(PoseStack.Pose pose, VertexConsumer c, float radius, int color) {
        FlatQuadContext sphere = new FlatQuadContext(pose, c);
        for (Vector3f v : NetherSphereVisual.unitSphereMesh()) {
            sphere.vertex(BLOCK_CENTER + v.x() * radius, BLOCK_CENTER + v.y() * radius,
                    BLOCK_CENTER + v.z() * radius, color, v.x(), v.y(), v.z());
        }
    }

    /**
     * Emits the shockwave ring: an annulus about the block center in the
     * plane the placed face lies in.
     *
     * @param pose         the pose entry
     * @param c            the vertex consumer
     * @param radius       the ring's outer radius in blocks
     * @param face         the placed face, whose axis the ring is square to
     * @param progressByte the explosion's progress as a byte
     */
    private static void emitRing(PoseStack.Pose pose, VertexConsumer c, float radius, Direction face,
                                 int progressByte) {
        int inner = ARGB.color(OPAQUE, progressByte, OPAQUE, 0);
        int outer = ARGB.color(OPAQUE, progressByte, OPAQUE, OPAQUE);
        FlatQuadContext ring = new FlatQuadContext(pose, c);
        float innerRadius = radius * RING_INNER;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = TWO_PI * i / RING_SEGMENTS;
            double a1 = TWO_PI * (i + 1) / RING_SEGMENTS;
            ringVertex(ring, face, a0, innerRadius, inner);
            ringVertex(ring, face, a0, radius, outer);
            ringVertex(ring, face, a1, radius, outer);
            ringVertex(ring, face, a1, innerRadius, inner);
        }
    }

    /**
     * Emits one ring vertex at an angle and radius in the plane square to the face.
     *
     * @param ring   the quad emitter
     * @param face   the placed face
     * @param angle  the angle about the face axis in radians
     * @param radius the distance from the center in blocks
     * @param color  the packed ring color
     */
    private static void ringVertex(FlatQuadContext ring, Direction face, double angle, float radius, int color) {
        float u = (float) Math.cos(angle) * radius;
        float v = (float) Math.sin(angle) * radius;
        float x = BLOCK_CENTER;
        float y = BLOCK_CENTER;
        float z = BLOCK_CENTER;
        switch (face.getAxis()) {
            case X -> {
                y += u;
                z += v;
            }
            case Y -> {
                x += u;
                z += v;
            }
            case Z -> {
                x += u;
                y += v;
            }
        }
        ring.vertex(x, y, z, color, face.getStepX(), face.getStepY(), face.getStepZ());
    }

    /**
     * Cubic ease-out: fast, then slow.
     *
     * @param t progress in [0, 1]
     * @return eased progress in [0, 1]
     */
    private static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }
}

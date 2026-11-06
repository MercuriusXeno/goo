package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.ColorSphere;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Meteo's meteor as a burning rock: a tumbling block of dark stone
 * whose faces glow ember toward its leading underside, wrapped in a halo of
 * fire, so it reads as a rock on fire both while it hangs and as it drops.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
public class MeteorRenderer extends EntityRenderer<Meteor, EntityRenderState> {

    /** The rock's edge, in blocks. */
    private static final float ROCK_SIZE = 1.3f;
    private static final float HALF = 0.5f;
    /** The rock's tumble, in degrees a tick about two axes. */
    private static final float TUMBLE_X = 7f;
    private static final float TUMBLE_Z = 4f;
    /** The rock's faces: its burning underside, its sides and its cooler top. */
    private static final int UNDERSIDE_RGB = 0xFF8C2A;
    private static final int SIDE_RGB = 0x5A2A16;
    private static final int TOP_RGB = 0x2A1A14;
    private static final int OPAQUE = 255;
    /** The fire halo around the rock. */
    private static final float HALO_RADIUS = 1.4f;
    private static final int HALO_ALPHA = 70;
    private static final int HALO_RGB = 0xFF6A1E;
    /** A corner's three coordinates in a quad's flat corner list, and each one's place. */
    private static final int AXES = 3;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    /**
     * @param context the renderer context
     */
    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, ROCK_SIZE * HALF, 0);
        collector.submitCustomGeometry(poseStack, GooRenderTypes.GLOW_SHELL_TYPE, (pose, buffer) ->
                ColorSphere.emit(pose, buffer, Vec3.ZERO, HALO_RADIUS, ARGB.color(HALO_ALPHA, HALO_RGB)));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.ageInTicks * TUMBLE_X));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * TUMBLE_Z));
        poseStack.scale(ROCK_SIZE, ROCK_SIZE, ROCK_SIZE);
        collector.submitCustomGeometry(poseStack, GooRenderTypes.SPORE_SHELL_TYPE, MeteorRenderer::emitRock);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /**
     * Emits the rock's six faces, a unit cube about the origin.
     *
     * @param pose   the pose
     * @param buffer the vertex consumer, position and color
     */
    private static void emitRock(PoseStack.Pose pose, VertexConsumer buffer) {
        int underside = ARGB.color(OPAQUE, UNDERSIDE_RGB);
        int side = ARGB.color(OPAQUE, SIDE_RGB);
        int top = ARGB.color(OPAQUE, TOP_RGB);
        quad(pose, buffer, underside, -HALF, -HALF, -HALF, HALF, -HALF, -HALF, HALF, -HALF, HALF, -HALF, -HALF, HALF);
        quad(pose, buffer, top, -HALF, HALF, -HALF, -HALF, HALF, HALF, HALF, HALF, HALF, HALF, HALF, -HALF);
        quad(pose, buffer, side, -HALF, -HALF, -HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF, HALF, -HALF, -HALF);
        quad(pose, buffer, side, -HALF, -HALF, HALF, HALF, -HALF, HALF, HALF, HALF, HALF, -HALF, HALF, HALF);
        quad(pose, buffer, side, -HALF, -HALF, -HALF, -HALF, -HALF, HALF, -HALF, HALF, HALF, -HALF, HALF, -HALF);
        quad(pose, buffer, side, HALF, -HALF, -HALF, HALF, HALF, -HALF, HALF, HALF, HALF, HALF, -HALF, HALF);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, float... corners) {
        for (int index = 0; index < corners.length; index += AXES) {
            buffer.addVertex(pose, corners[index + X], corners[index + Y], corners[index + Z]).setColor(color);
        }
    }
}

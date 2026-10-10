package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.model.OculusModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * The oculus prism: once its combo takes, the quartz column itself morphs
 * into the eye, its shaft drawing in to a small lens floating just off the
 * face as the quartz turns ender green, one smooth transformation; from then
 * on the eye hovers there, turned toward the camera drawing it, its lids shut
 * unless the viewer looks at it. Purely cosmetic: each client turns and opens
 * the eye for its own camera.
 * Decisions oculus-prism-becomes-a-hovering-eye and model-transformation-is-one-animation.
 */
public final class OculusStyle implements PrismComboStyle {

    /** Ticks the column takes to become the eye. */
    static final float TRANSFORM_TICKS = 24f;
    /** Blocks the eye bobs either side of where it hovers. */
    static final float HOVER_AMPLITUDE = 0.03f;
    /** Ticks per bob. */
    static final float HOVER_PERIOD = 60f;
    /** How much of the eye's front each lid covers when shut, in blocks. */
    static final float LID_REACH = 2.5f / 16f;
    /** Where the eye's front starts, in the eye model's blocks. */
    static final float EYE_BOTTOM = 5.5f / 16f;
    /** Where the eye's front ends, in the eye model's blocks. */
    static final float EYE_TOP = 10.5f / 16f;
    /** The eye's side texture, which the lens wears as the column turns into it. */
    private static final Identifier EYE_SIDE = Identifier.fromNamespaceAndPath(Goo.MODID, "block/oculus_eye_side");
    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float HALF = 0.5f;
    private static final int OPAQUE = 255;
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;
    private static final double TWO_PI = 2 * Math.PI;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float share = transformationShare(state.gameTime, state.comboSince);
        if (share < 1f) {
            submitMorph(state, poseStack, nodeCollector, smoothstep(share));
        } else {
            submitEye(state, poseStack, nodeCollector);
        }
    }

    /**
     * Draws the column part way into the eye's lens, the quartz fading out as
     * the eye's green fades in over the one morphing mesh.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     * @param morph         how far the column has become the lens, 0 to 1
     */
    private static void submitMorph(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                    float morph) {
        CrystalClusterSubmitter.Look quartz = state.look;
        if (quartz == null) {
            return;
        }
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        List<Vec3[]> faces = OculusMorph.faces(morph);
        CrystalClusterSubmitter.Look green = new CrystalClusterSubmitter.Look(
                GooSubmitter.spriteUv(GooSubmitter.blockSprite(EYE_SIDE)),
                ARGB.color(Math.round(morph * OPAQUE), GooRenderUtil.OPAQUE_WHITE));
        CrystalClusterSubmitter.submitFaces(poseStack, nodeCollector, faces, PrismCrystal.fade(quartz, 1f - morph),
                state.lightCoords);
        CrystalClusterSubmitter.submitFaces(poseStack, nodeCollector, faces, green, state.lightCoords);
        poseStack.popPose();
    }

    /**
     * Draws the hovering eye and its lids, turned toward the camera.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     */
    private static void submitEye(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        Vec3 eye = eyeInCell(state.facing).add(Vec3.atLowerCornerOf(state.facing.getUnitVec3i())
                .scale(hover(state.gameTime - state.comboSince - TRANSFORM_TICKS)));
        poseStack.pushPose();
        poseStack.translate(eye.x, eye.y, eye.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yawToCamera));
        poseStack.translate(-HALF, -HALF, -HALF);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, state.lightCoords, OculusModels.eye());
        float shut = state.lidClosure;
        if (shut > 0f) {
            submitLid(state, poseStack, nodeCollector, EYE_TOP - shut * LID_REACH, shut);
            submitLid(state, poseStack, nodeCollector, EYE_BOTTOM, shut);
        }
        poseStack.popPose();
    }

    /**
     * Draws one lid over the eye's front, as tall as the lids stand shut.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose in the eye's frame
     * @param nodeCollector the submit collector
     * @param bottom        the lid's bottom edge, in blocks
     * @param shut          how shut the lids stand, 0 to 1
     */
    private static void submitLid(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  float bottom, float shut) {
        poseStack.pushPose();
        poseStack.translate(0f, bottom, 0f);
        poseStack.scale(1f, shut * LID_REACH * PIXELS_PER_BLOCK, 1f);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, state.lightCoords, OculusModels.lid());
        poseStack.popPose();
    }

    /**
     * Where the eye's middle hovers in its cell: just off the face the prism
     * grew from, where the column's lens ends.
     *
     * @param facing the prism's facing, pointing out of the face it grew from
     * @return the eye's middle in the cell's own blocks
     */
    public static Vec3 eyeInCell(Direction facing) {
        Vec3 out = Vec3.atLowerCornerOf(facing.getUnitVec3i());
        return new Vec3(HALF, HALF, HALF).subtract(out.scale(HALF))
                .add(out.scale(OculusMorph.EYE_LIFT / PIXELS_PER_BLOCK));
    }

    /**
     * How far through its transformation the oculus stands.
     *
     * @param gameTime   the game time with the partial tick
     * @param comboSince the game time the combo took
     * @return 0 as the combo takes, 1 once the eye stands whole
     */
    static float transformationShare(float gameTime, long comboSince) {
        return Math.clamp((gameTime - comboSince) / TRANSFORM_TICKS, 0f, 1f);
    }

    /**
     * The eye's bob off where it hovers, starting still as the eye forms.
     *
     * @param sinceFormed the ticks since the eye stood whole
     * @return the offset in blocks
     */
    static float hover(float sinceFormed) {
        return HOVER_AMPLITUDE * (float) Math.sin(TWO_PI * sinceFormed / HOVER_PERIOD);
    }

    /**
     * The smoothstep ease: still at both ends, fastest at the middle.
     *
     * @param share how far along, 0 to 1
     * @return the eased share
     */
    static float smoothstep(float share) {
        return share * share * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * share);
    }
}

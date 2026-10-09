package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.model.OculusModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/**
 * The oculus prism: once its combo takes, the crystal retracts into the face
 * it grew from as the eye grows out of nothing in the middle of the cell,
 * one smooth transformation; from then on the eye hovers, turned toward the
 * camera drawing it, and now and then its lids close and open in a blink.
 * Purely cosmetic: each client turns the eye toward its own camera.
 * Decisions oculus-prism-becomes-a-hovering-eye and model-transformation-is-one-animation.
 */
public final class OculusStyle implements PrismComboStyle {

    /** Ticks the crystal takes to retract and the eye to grow. */
    static final float TRANSFORM_TICKS = 24f;
    /** Blocks the eye bobs either side of the cell's middle. */
    static final float HOVER_AMPLITUDE = 0.06f;
    /** Ticks per bob. */
    static final float HOVER_PERIOD = 60f;
    /** Ticks between one blink and the next. */
    static final int BLINK_PERIOD = 100;
    /** Ticks a blink takes to close and open again. */
    static final int BLINK_TICKS = 6;
    /** How far each lid travels toward the eye's middle to close it, in blocks. */
    static final float LID_TRAVEL = 3f / 16f;
    /** Where the top lid rests open, above the eye, in blocks. */
    static final float TOP_LID_OPEN = 11f / 16f;
    /** Where the bottom lid rests open, under the eye, in blocks. */
    static final float BOTTOM_LID_OPEN = 1.5f / 16f;
    private static final float HALF = 0.5f;
    /** The transformation's halves: the crystal retracts over the first, the eye grows over the second. */
    private static final float HALVES = 2f;
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final int NO_OUTLINE = 0;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float share = transformationShare(state.gameTime, state.comboSince);
        float crystalScale = 1f - smoothstep(Math.min(1f, HALVES * share));
        if (crystalScale > 0f) {
            poseStack.pushPose();
            scaleAboutBase(poseStack, state.facing, crystalScale);
            state.crystal.submit(poseStack, nodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, NO_OUTLINE);
            poseStack.popPose();
        }
        float eyeScale = share < HALF ? 0f : smoothstep(HALVES * share - 1f);
        if (eyeScale > 0f) {
            submitEye(state, poseStack, nodeCollector, eyeScale);
        }
    }

    /**
     * Draws the hovering eye and its lids, turned toward the camera.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     * @param eyeScale      the eye's size, 0 to 1
     */
    private static void submitEye(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  float eyeScale) {
        poseStack.pushPose();
        poseStack.translate(HALF, HALF + hover(state.gameTime), HALF);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yawToCamera));
        poseStack.scale(eyeScale, eyeScale, eyeScale);
        poseStack.translate(-HALF, -HALF, -HALF);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, state.lightCoords, OculusModels.eye());
        float closed = lidClosure(state.gameTime);
        submitLid(state, poseStack, nodeCollector, TOP_LID_OPEN - closed * LID_TRAVEL);
        submitLid(state, poseStack, nodeCollector, BOTTOM_LID_OPEN + closed * LID_TRAVEL);
        poseStack.popPose();
    }

    /**
     * Draws one lid with its base at a height.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the eye's frame
     * @param nodeCollector the submit collector
     * @param baseHeight    the lid's base, in blocks
     */
    private static void submitLid(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  float baseHeight) {
        poseStack.pushPose();
        poseStack.translate(0f, baseHeight, 0f);
        GooSubmitter.submitBakedBody(poseStack, nodeCollector, state.lightCoords, OculusModels.lid());
        poseStack.popPose();
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
     * The eye's bob off the cell's middle.
     *
     * @param gameTime the game time with the partial tick
     * @return the offset in blocks
     */
    static float hover(float gameTime) {
        return HOVER_AMPLITUDE * (float) Math.sin(TWO_PI * gameTime / HOVER_PERIOD);
    }

    /**
     * How closed the lids stand: shut at the middle of each blink, open the
     * rest of the period.
     *
     * @param gameTime the game time with the partial tick
     * @return 0 open, 1 shut
     */
    static float lidClosure(float gameTime) {
        float intoBlink = gameTime % BLINK_PERIOD;
        float halfBlink = BLINK_TICKS * HALF;
        return intoBlink >= BLINK_TICKS ? 0f : 1f - Math.abs(intoBlink - halfBlink) / halfBlink;
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

    /**
     * Scales the pose about the centre of the face the prism grew from, so
     * the crystal retracts into the face.
     *
     * @param poseStack the pose at the cell's corner
     * @param facing    the face the prism grew from
     * @param scale     the crystal's size
     */
    private static void scaleAboutBase(PoseStack poseStack, Direction facing, float scale) {
        float baseX = HALF - HALF * facing.getStepX();
        float baseY = HALF - HALF * facing.getStepY();
        float baseZ = HALF - HALF * facing.getStepZ();
        poseStack.translate(baseX, baseY, baseZ);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-baseX, -baseY, -baseZ);
    }
}

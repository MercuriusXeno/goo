package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ber.OculusEye;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * An oculus prism a world saved before the oculus block still holds: the eye ({@link OculusEye}) hovering low off
 * the face the prism grew from. No blink makes one any more, and Blink snaps only to the oculus block.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
public final class OculusStyle implements PrismComboStyle {

    /** Ticks a combo's transformation runs, which the other combo styles fold their looks over. */
    static final float TRANSFORM_TICKS = 24f;
    /** Where the eye's middle hovers off the prism's face, in model pixels. */
    private static final double EYE_LIFT = 4;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        OculusEye.submit(new OculusEye.Frame(eyeInCell(state.facing), state.facing,
                state.gameTime - state.comboSince, state.yawToCamera, state.lidClosure, state.lightCoords),
                poseStack, nodeCollector);
    }

    /**
     * Where an oculus prism's eye hovers in its cell: just off the face the prism grew from.
     *
     * @param facing the prism's facing, pointing out of the face it grew from
     * @return the eye's middle in the cell's own blocks
     */
    public static Vec3 eyeInCell(Direction facing) {
        return OculusEye.eyeInCell(facing, EYE_LIFT);
    }

    /**
     * How far through its transformation a combo stands.
     *
     * @param gameTime   the game time with the partial tick
     * @param comboSince the game time the combo took
     * @return 0 as the combo takes, 1 once it stands whole
     */
    static float transformationShare(float gameTime, long comboSince) {
        return Math.clamp((gameTime - comboSince) / TRANSFORM_TICKS, 0f, 1f);
    }
}

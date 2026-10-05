package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The collector an afterimage's entity renderer submits through so the
 * ripple learns where the body stands: it keeps the pose the renderer
 * gives the body model, the scale, turn and flip it applies, and draws
 * nothing.
 * Decision afterimage-is-one-shared-effect.
 */
final class BodyPoseCapture extends BodyOnlyCollector {

    private @Nullable Matrix4f rootPose;
    private @Nullable Runnable poseBody;

    /**
     * @param body the entity renderer's body model
     */
    BodyPoseCapture(Model<?> body) {
        super(body);
    }

    /**
     * The body model root's pose in camera space, as the renderer placed it.
     *
     * @return the pose, or null where the renderer submitted no body
     */
    @Nullable Matrix4f rootPose() {
        return rootPose;
    }

    /**
     * Poses the shared body model for the echoed entity's state, as the
     * renderer would when it draws.
     */
    void poseBody() {
        if (poseBody != null) {
            poseBody.run();
        }
    }

    @Override
    <S> void onBody(Model<? super S> model, S state, PoseStack poseStack, int lightCoords) {
        rootPose = new Matrix4f(poseStack.last().pose());
        poseBody = () -> model.setupAnim(state);
    }
}

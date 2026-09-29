package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * What one frame hands every burnout it draws.
 *
 * @param poseStack the level's pose stack, camera relative
 * @param buffers   the buffer source the frame draws into
 * @param camera    the camera's world position
 * @param gameTime  the level's game time including the partial tick
 */
public record BurnoutFrame(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                           Vec3 camera, float gameTime) {
}

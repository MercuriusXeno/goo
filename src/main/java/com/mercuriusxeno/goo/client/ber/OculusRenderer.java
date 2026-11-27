package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.alientech.FaceMountedBlock;
import com.mercuriusxeno.goo.block.alientech.OculusBlock;
import com.mercuriusxeno.goo.block.alientech.OculusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the oculus's ender eye hovering off its base, turned toward whoever looks at it, its lids open only while
 * the viewer's look rests on it; the base is the block model.
 * decision oculus-prism-becomes-a-hovering-eye
 */
public class OculusRenderer implements BlockEntityRenderer<OculusBlockEntity, OculusRenderState> {

    /**
     * Creates the oculus renderer.
     *
     * @param context the renderer context
     */
    public OculusRenderer(BlockEntityRendererProvider.Context context) {
        // The eye is a standalone model; the context carries nothing it draws from.
    }

    @Override
    public OculusRenderState createRenderState() {
        return new OculusRenderState();
    }

    @Override
    public void extractRenderState(OculusBlockEntity oculus, OculusRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(oculus, state, breakProgress);
        state.facing = oculus.getBlockState().getValue(FaceMountedBlock.FACING);
        state.gameTime = oculus.getLevel() == null ? 0f : oculus.getLevel().getGameTime() + partialTick;
        Vec3 eye = Vec3.atLowerCornerOf(oculus.getBlockPos()).add(OculusEye.eyeInCell(state.facing,
                OculusBlock.EYE_LIFT));
        state.yawToCamera = yawToward(eye, cameraPos);
        Vec3 forward = new Vec3(Minecraft.getInstance().gameRenderer.mainCamera().forwardVector());
        state.lidClosure = OculusLids.closure(oculus.getBlockPos(), OculusLids.lookedAt(cameraPos, forward, eye),
                state.gameTime);
    }

    /**
     * The yaw that turns a model at a point toward the camera, about the
     * vertical: 0 faces south, as a model's front does.
     *
     * @param from      the model's centre
     * @param cameraPos the camera
     * @return the yaw in degrees
     */
    static float yawToward(Vec3 from, Vec3 cameraPos) {
        return (float) Math.toDegrees(Math.atan2(cameraPos.x - from.x, cameraPos.z - from.z));
    }

    @Override
    public void submit(OculusRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        OculusEye.submit(new OculusEye.Frame(OculusEye.eyeInCell(state.facing, OculusBlock.EYE_LIFT), state.facing,
                state.gameTime, state.yawToCamera, state.lidClosure, state.lightCoords), poseStack, nodeCollector);
    }
}

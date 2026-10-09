package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.gate.DragonGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Draws a Dragon Gate cell as the End portal's starfield over the whole
 * cell, on each face that looks out of the gate.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public class DragonGateRenderer extends AbstractEndPortalRenderer<DragonGateBlockEntity, EndPortalRenderState> {

    /**
     * Creates the gate renderer.
     *
     * @param context the renderer context
     */
    public DragonGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public EndPortalRenderState createRenderState() {
        return new EndPortalRenderState();
    }

    @Override
    public void submit(EndPortalRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera) {
        submitCube(state.facesToShow, RenderTypes.endPortal(), poseStack, submitNodeCollector);
    }
}

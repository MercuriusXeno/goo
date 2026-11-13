package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.outsider.DimensionDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a room doorway cell's share of the doorway as the End portal's
 * starfield: a flat quad filling the cell east to west in the plane through
 * its middle, where the swirling sheet stood, seen from either side. The
 * doorway's two cells together stand the one wide, two tall doorway the
 * conjured door opens into, and the shader's screen-space starfield reads
 * them as one rectangle.
 * decision room-door-wears-the-end-portal-starfield
 */
public class DimensionDoorRenderer implements BlockEntityRenderer<DimensionDoorBlockEntity, BlockEntityRenderState> {

    /** The plane through the cell's middle, running east to west. */
    private static final float MIDDLE = 0.5f;
    /** The cell's face in that plane, in order around the quad. */
    private static final Vector3f[] FACE = {
        new Vector3f(0, 0, MIDDLE), new Vector3f(1, 0, MIDDLE), new Vector3f(1, 1, MIDDLE),
        new Vector3f(0, 1, MIDDLE)};

    /**
     * Creates the doorway renderer.
     *
     * @param context the renderer context, which carries nothing the doorway draws from
     */
    public DimensionDoorRenderer(BlockEntityRendererProvider.@Nullable Context context) {
        // The doorway draws from its cell alone.
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void extractRenderState(DimensionDoorBlockEntity door, BlockEntityRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(door, state, breakProgress);
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        nodeCollector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, buffer) -> {
            for (Vector3f corner : FACE) {
                buffer.addVertex(pose, corner);
            }
            for (int index = FACE.length - 1; index >= 0; index--) {
                buffer.addVertex(pose, FACE[index]);
            }
        });
    }
}

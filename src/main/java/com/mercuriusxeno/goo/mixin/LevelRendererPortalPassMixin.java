package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.PortalView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

/**
 * A portal view's render leaves the frame's view area and section
 * occlusion where the main camera put them. Minecraft 26.2's level render
 * re-centres the view area on its camera and updates the occlusion graph
 * from it, which 26.1's renderLevel left to the frame; a view drawn from a
 * camera mirrored through a far portal would re-centre the area and
 * restart the graph twice a frame.
 * portals-link-by-looking-through-portal-vision
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererPortalPassMixin {

    /**
     * Re-centres the view area on the frame's camera alone.
     *
     * @param renderer the level renderer
     * @param camera   the camera of the pass rendering
     * @return whether the view area follows the pass's camera
     */
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;repositionCamera("
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private boolean goo$repositionForTheFrameAlone(LevelRenderer renderer, CameraRenderState camera) {
        return !PortalView.drawing();
    }

    /**
     * Updates the section occlusion from the frame's camera alone.
     *
     * @param graph   the occlusion graph
     * @param camera  the camera of the pass rendering
     * @param fov     the field of view
     * @param loading the chunk loading the pass extracted
     * @return whether the graph follows the pass's camera
     */
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update("
                    + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I"
                    + "Lnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"))
    private boolean goo$occludeForTheFrameAlone(SectionOcclusionGraph graph, CameraRenderState camera, int fov,
                                                ChunkLoadingRenderState loading) {
        return !PortalView.drawing();
    }
}

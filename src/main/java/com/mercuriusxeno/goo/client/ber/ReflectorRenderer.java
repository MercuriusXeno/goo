package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.alientech.ReflectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a reflector's light rails; its lensed cuboid is the block model. The beams draw from as far as the render
 * distance reaches, and the box the renderer draws within reaches every reflector it links to.
 * decision reflector-rails-carry-the-brightest-light
 */
public class ReflectorRenderer implements BlockEntityRenderer<ReflectorBlockEntity, ReflectorRenderState> {

    /**
     * Creates the reflector renderer.
     *
     * @param context the renderer context
     */
    public ReflectorRenderer(BlockEntityRendererProvider.Context context) {
        // The beams are procedural; the context carries nothing they draw from.
    }

    @Override
    public ReflectorRenderState createRenderState() {
        return new ReflectorRenderState();
    }

    @Override
    public void extractRenderState(ReflectorBlockEntity reflector, ReflectorRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(reflector, state, breakProgress);
        state.animationTime = (reflector.getLevel() == null ? 0L : reflector.getLevel().getGameTime()) + partialTick;
        state.links = reflector.getLinks().stream()
                .map(link -> Vec3.atLowerCornerOf(link.subtract(reflector.getBlockPos()))).toList();
        state.linkLight = reflector.getLinkLight();
    }

    @Override
    public void submit(ReflectorRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        ReflectorBeams.queue(poseStack, Vec3.atCenterOf(state.blockPos), state.links, state.linkLight,
                state.animationTime);
    }

    @Override
    public AABB getRenderBoundingBox(ReflectorBlockEntity reflector) {
        AABB bounds = new AABB(reflector.getBlockPos());
        for (BlockPos link : reflector.getLinks()) {
            bounds = bounds.minmax(new AABB(link));
        }
        return bounds;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * SectionPos.SECTION_SIZE;
    }
}

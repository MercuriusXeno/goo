package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.MeltingBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a melting block: the block it stands in for, in its own textures,
 * melting like wax in place while patches of its own goo spread over it
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlockEntityRenderer implements BlockEntityRenderer<MeltingBlockEntity, MeltingBlockRenderState> {

    /**
     * Creates the melting block renderer.
     *
     * @param context the renderer context
     */
    public MeltingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // the melt is drawn from the original block's own model, looked up each frame
    }

    @Override
    public MeltingBlockRenderState createRenderState() {
        return new MeltingBlockRenderState();
    }

    @Override
    public void extractRenderState(MeltingBlockEntity melting, MeltingBlockRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(melting, state, breakProgress);
        state.original = melting.original();
        state.level = melting.getLevel() instanceof BlockAndTintGetter tinted ? tinted : null;
        state.goo = MeltMeshGoo.of(melting.original());
        state.ticks = melting.getLevel() == null ? 0f : melting.getLevel().getGameTime() + partialTick;
        state.melted = MeltingBlocks.CLIENT.meltedAt(melting.getBlockPos(), state.ticks).orElse(0f);
    }

    @Override
    public void submit(MeltingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        if (state.level == null || state.original.isAir()) {
            return;
        }
        MeltMesh.Melt melt = new MeltMesh.Melt(state.original, state.level, state.blockPos, state.goo, state.melted,
                state.ticks, true);
        GooSubmitter.submitBody(poseStack, nodeCollector, state.lightCoords, ctx -> MeltMesh.emit(ctx, melt));
    }
}

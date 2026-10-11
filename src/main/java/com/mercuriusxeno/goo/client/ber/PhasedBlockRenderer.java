package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.quantum.PhasedBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a phased block as a ghost of the block it holds: every quad of the
 * held block's model, translucent and washed pale, so the hole reads as the
 * wall still standing out of phase.
 * portable-hole-phases-blocks-for-a-while
 */
public class PhasedBlockRenderer implements BlockEntityRenderer<PhasedBlockEntity, PhasedBlockRenderer.State> {

    /** The ghost's color: a pale cold wash at under half alpha. */
    static final int GHOST_COLOR = ARGB.color(96, 0xC8D4FF);
    /** The seed every ghost's model parts are picked with, so a ghost never flickers between variants. */
    private static final long MODEL_SEED = 42L;

    /** What one frame draws of a phased block. */
    public static class State extends BlockEntityRenderState {
        /** The held block's quads, empty while it holds air. */
        public List<BakedQuad> quads = List.of();
    }

    /**
     * Creates the phased block renderer.
     *
     * @param context the renderer context
     */
    public PhasedBlockRenderer(BlockEntityRendererProvider.Context context) {
        // The ghost is drawn from the held block's model; the context carries nothing it draws from.
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PhasedBlockEntity phased, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(phased, state, breakProgress);
        state.quads = quadsOf(phased);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        if (state.quads.isEmpty()) {
            return;
        }
        QuadInstance instance = new QuadInstance();
        instance.setColor(GHOST_COLOR);
        instance.setLightCoords(state.lightCoords);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        List<BakedQuad> quads = state.quads;
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, buffer) -> {
            for (BakedQuad quad : quads) {
                buffer.putBakedQuad(pose, quad, instance);
            }
        });
    }

    private static List<BakedQuad> quadsOf(PhasedBlockEntity phased) {
        BlockState held = phased.held();
        ClientLevel level = Minecraft.getInstance().level;
        if (held.isAir() || level == null) {
            return List.of();
        }
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(held);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, phased.getBlockPos(), held, RandomSource.create(MODEL_SEED), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                quads.addAll(part.getQuads(side));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }
}

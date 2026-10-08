package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.GooSag;
import com.mercuriusxeno.goo.client.ability.MeltingBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a melting block: a goo copy of the block it stands in for, sagging in
 * place as the unmake melts it, slumping lower with its foot spreading and its
 * top rounding (decision unmake-waves-dissolve-by-crucible-cost).
 */
public class MeltingBlockEntityRenderer implements BlockEntityRenderer<MeltingBlockEntity, MeltingBlockRenderState> {

    /** The goo copy's alpha, translucent enough to read as goo. */
    private static final int GOO_ALPHA = 0xE0;

    /**
     * Creates the melting block renderer.
     *
     * @param context the renderer context
     */
    public MeltingBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // the goo copy needs no baked models
    }

    @Override
    public MeltingBlockRenderState createRenderState() {
        return new MeltingBlockRenderState();
    }

    @Override
    public void extractRenderState(MeltingBlockEntity melting, MeltingBlockRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(melting, state, breakProgress);
        float now = melting.getLevel() == null ? 0f : melting.getLevel().getGameTime() + partialTick;
        state.melted = MeltingBlocks.CLIENT.meltedAt(melting.getBlockPos(), now).orElse(0f);
    }

    @Override
    public void submit(MeltingBlockRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        int color = ARGB.color(GOO_ALPHA, GooRenderUtil.OPAQUE_WHITE);
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(GooTypes.UNSTABLE));
        GooSubmitter.submitFluid(poseStack, nodeCollector, ctx -> {
            for (CuboidBounds layer : GooSag.layers(1f, 1f, 1f, state.melted)) {
                ctx.emitBox(color, layer, uv);
            }
        });
    }
}

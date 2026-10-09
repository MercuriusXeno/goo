package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.ArrayList;
import java.util.List;

/**
 * A proxy collector a block model submits into, which re-emits the model's
 * quads multiplied by one color, so a combined prism's crystal draws tinted:
 * Verdant's leaf-green.
 * verdant-prism-greens-blocks-slowly
 */
public final class TintedBlockCollector extends ItemQuadCollector {

    private final int tint;

    /**
     * Creates the collector.
     *
     * @param delegate the real collector the tinted quads go to
     * @param tint     the packed ARGB color every quad is multiplied by
     */
    public TintedBlockCollector(SubmitNodeCollector delegate, int tint) {
        super(delegate);
        this.tint = tint;
    }

    /**
     * Passes custom geometry through untinted: the crystal's model carries none.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        delegate.submitCustomGeometry(poseStack, renderType, renderer);
    }

    /**
     * Drops items: the crystal's model submits none.
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords,
                           int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        // The crystal's block model submits no item.
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                                 int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                quads.addAll(part.getQuads(side));
            }
            quads.addAll(part.getQuads(null));
        }
        resubmitByAtlas(poseStack, quads, tintLayers, GooSubmitter::translucentOn, (instance, quadTint) -> {
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(overlayCoords);
            instance.setColor(ARGB.multiply(tint, quadTint));
        });
    }
}

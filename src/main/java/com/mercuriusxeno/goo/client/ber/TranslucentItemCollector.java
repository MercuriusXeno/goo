package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.List;

/**
 * Proxy SubmitNodeCollector that intercepts submitItem calls and re-emits
 * the item's quads through submitCustomGeometry with a translucent render
 * type and per-vertex alpha. Used by the plexer BER for ghost item previews.
 */
class TranslucentItemCollector extends ItemQuadCollector {

    /**
     * Fully opaque white - used to construct the alpha-tinted base color.
     */
    private static final int OPAQUE_WHITE = 0xFF;

    private final int alphaColor;

    /**
     * Creates a proxy that renders item quads with translucent alpha.
     *
     * @param delegate the real collector to emit translucent geometry into
     * @param alpha    alpha value 0-255
     */
    TranslucentItemCollector(SubmitNodeCollector delegate, int alpha) {
        super(delegate);
        this.alphaColor = ARGB.color(alpha, OPAQUE_WHITE, OPAQUE_WHITE, OPAQUE_WHITE);
    }

    /**
     * Intercepts item quads and re-emits them as translucent custom geometry,
     * tint colors combined with alpha.
     *
     * @param poseStack      the pose stack
     * @param displayContext the item display context
     * @param lightCoords    packed light coordinates
     * @param overlayCoords  packed overlay coordinates
     * @param outlineColor   outline color (unused for ghost rendering)
     * @param tintLayers     per-tint-index colors from the item color handler
     * @param quads          the baked quads to render
     * @param foilType       the foil/glint type (unused for ghost rendering)
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext,
                           int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        resubmitByAtlas(poseStack, quads, tintLayers, GooSubmitter::translucentOn, (instance, tint) -> {
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(overlayCoords);
            instance.setColor(ARGB.multiply(alphaColor, tint));
        });
    }

    /**
     * Re-emits custom geometry with alpha-tinted vertex colors.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     CustomGeometryRenderer renderer) {
        delegate.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                renderer.render(pose, new AlphaTintConsumer(buffer, alphaColor)));
    }

    /**
     * VertexConsumer wrapper that multiplies alpha into all vertex colors.
     */
    private static final class AlphaTintConsumer implements VertexConsumer {
        private final VertexConsumer inner;
        private final int tint;

        AlphaTintConsumer(VertexConsumer inner, int tint) {
            this.inner = inner;
            this.tint = tint;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(ARGB.multiply(tint, ARGB.color(a, r, g, b)));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            inner.setColor(ARGB.multiply(tint, color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            inner.setLineWidth(width);
            return this;
        }
    }
}

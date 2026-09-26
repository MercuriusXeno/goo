package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.List;

/**
 * Proxy SubmitNodeCollector that re-emits a melting item's quads on the dissolve
 * render type, each vertex carrying the dissolve fraction and glow color in its
 * overlay coordinates (decision dissolve-shader-on-item).
 */
class DissolvingItemCollector extends ItemQuadCollector {

    private final DissolveGlow glow;

    /**
     * Creates a proxy that dissolves the item it is handed.
     *
     * @param delegate the real collector to emit the dissolving geometry into
     * @param glow     how far the item has dissolved and the color its edge glows
     */
    DissolvingItemCollector(SubmitNodeCollector delegate, DissolveGlow glow) {
        super(delegate);
        this.glow = glow;
    }

    /**
     * Re-emits the item's quads on the dissolve render type, lit as submitted,
     * their overlay slot carrying the dissolve.
     *
     * @param poseStack      the pose stack
     * @param displayContext the item display context
     * @param lightCoords    packed light coordinates
     * @param overlayCoords  packed overlay coordinates, replaced by the dissolve
     * @param outlineColor   outline color (unused)
     * @param tintLayers     per-tint-index colors from the item color handler
     * @param quads          the baked quads to render
     * @param foilType       the foil/glint type (unused)
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext,
                           int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        int dissolveCoords = glow.overlayCoords();
        resubmitByAtlas(poseStack, quads, tintLayers, GooRenderTypes::crucibleDissolve, (instance, tint) -> {
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(dissolveCoords);
            instance.setColor(tint);
        });
    }

    /**
     * Drops custom geometry a special item model submits; only baked quads dissolve.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     CustomGeometryRenderer renderer) {
        // Only baked item quads carry the dissolve (decision dissolve-shader-on-item).
    }
}

package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import java.util.List;

/**
 * Proxy SubmitNodeCollector that re-emits one tile of a melting item's quads on the dissolve
 * render type once per glow layer, largest type first, each vertex carrying the
 * dissolve fraction, the layer's color, share and index (decisions dissolve-shader-on-item,
 * glow-color-from-mingling). The layers emit in order within one submission, so a later
 * layer's glow draws over an earlier one's and each fragment ends in one type.
 */
class DissolvingItemCollector extends ItemQuadCollector {

    private final DissolveGlow glow;
    private final ItemTileClipper.Tile tile;

    /**
     * Creates a proxy that dissolves the one tile of the item it is handed.
     *
     * @param delegate the real collector to emit the dissolving geometry into
     * @param glow     how far the item has dissolved and the layers its edge glows in
     * @param tile     the tile of the item's image this proxy emits
     */
    DissolvingItemCollector(SubmitNodeCollector delegate, DissolveGlow glow, ItemTileClipper.Tile tile) {
        super(delegate);
        this.glow = glow;
        this.tile = tile;
    }

    /**
     * Re-emits the item's quads on the dissolve render type, once per glow layer.
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
        resubmitByAtlas(poseStack, quads, GooRenderTypes::crucibleDissolve, (pose, buffer, group) -> {
            for (DissolveGlow.Layer layer : glow.layers()) {
                int overlay = glow.overlayCoords(layer);
                int light = DissolveGlow.lightCoords(lightCoords, layer);
                for (BakedQuad quad : group) {
                    emitTilePieces(pose, buffer, quad, new QuadCoords(tintOf(quad, tintLayers), overlay, light));
                }
            }
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

    /**
     * The per-vertex values one quad emits with.
     *
     * @param tint    the quad's tint color
     * @param overlay the overlay coordinates
     * @param light   the lightmap coordinates
     */
    private record QuadCoords(int tint, int overlay, int light) {
    }

    /**
     * Emits the pieces of one quad inside this proxy's tile with the coordinates given
     * verbatim, where putBakedQuad would fold a quad's light emission into the lightmap
     * coordinates the layer's share rides in.
     *
     * @param pose   the pose the geometry was submitted at
     * @param buffer the buffer to emit into
     * @param quad   the baked quad
     * @param coords the tint, overlay and lightmap coordinates
     */
    private void emitTilePieces(PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, QuadCoords coords) {
        Vector3f normal = pose.transformNormal(quad.direction().getUnitVec3f(), new Vector3f());
        for (List<ItemTileClipper.ClipVertex> piece : ItemTileClipper.clip(ItemTileClipper.verticesOf(quad), tile)) {
            for (ItemTileClipper.ClipVertex vertex : piece) {
                Vector3f position = pose.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f());
                buffer.addVertex(position.x(), position.y(), position.z(),
                        ARGB.multiply(coords.tint(), vertex.color()), vertex.u(), vertex.v(),
                        coords.overlay(), coords.light(), normal.x(), normal.y(), normal.z());
            }
        }
    }
}

package com.mercuriusxeno.goo.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an item's model the way its layers submit it, before any shard draws, and breaks it
 * into shards (decision tiles-of-the-items-image): every quad in the space the bounding box
 * measures, each cell's opacity read from the quad's own sprite, and the texel size from the
 * largest quad's sprite rather than assumed 16, so larger textures break along their own pixels.
 */
final class ItemModelProbe extends ItemQuadCollector {

    /** The texels across a model's larger side when no quad names a sprite with pixels. */
    static final int DEFAULT_TEXELS = 16;

    private final List<ShardedModel.CapturedQuad> quads = new ArrayList<>();
    private int texelsPerSpan = DEFAULT_TEXELS;
    private float largestArea;
    /** A model this probe checks the item's quads against instead of recording them, or null. */
    private @Nullable ShardedModel checking;
    private boolean covered = true;

    private ItemModelProbe() {
        super(null);
    }

    /**
     * Probes an item and breaks its model by its seed.
     *
     * @param item the resolved item model
     * @param seed the item's seed
     * @return the sharded model
     */
    static ShardedModel probe(ItemStackRenderState item, long seed) {
        ItemModelProbe probe = new ItemModelProbe();
        item.submit(new PoseStack(), probe, 0, OverlayTexture.NO_OVERLAY, 0);
        return ShardedModel.cut(probe.quads, item.getModelBoundingBox(), probe.texelsPerSpan, seed);
    }

    /**
     * Answers whether a sharded model was cut from the quads an item submits now; after a
     * resource reload the item's quads are baked anew and the old cut finds none of them.
     *
     * @param model the sharded model
     * @param item  the resolved item model
     * @return true if every quad the item submits has a cut in the model
     */
    static boolean covers(ShardedModel model, ItemStackRenderState item) {
        ItemModelProbe probe = new ItemModelProbe();
        probe.checking = model;
        item.submit(new PoseStack(), probe, 0, OverlayTexture.NO_OVERLAY, 0);
        return probe.covered;
    }

    /**
     * Records every quad of the layer in the space the bounding box measures.
     *
     * @param poseStack      the pose stack, the layer's own transforms applied
     * @param displayContext the item display context
     * @param lightCoords    packed light coordinates (unused)
     * @param overlayCoords  packed overlay coordinates (unused)
     * @param outlineColor   outline color (unused)
     * @param tintLayers     per-tint-index colors (unused)
     * @param layerQuads     the baked quads
     * @param foilType       the foil type (unused)
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                           int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> layerQuads,
                           ItemStackRenderState.FoilType foilType) {
        if (checking != null) {
            for (BakedQuad quad : layerQuads) {
                if (checking.cutOf(quad) == null) {
                    covered = false;
                }
            }
            return;
        }
        for (BakedQuad quad : layerQuads) {
            List<QuadRectClipper.ClipVertex> inGrid = QuadRectClipper.verticesOf(quad).stream()
                    .map(vertex -> vertex.moved(poseStack.last().pose())).toList();
            Vector3f outward = poseStack.last().pose()
                    .transformDirection(quad.direction().getUnitVec3f(), new Vector3f());
            TextureAtlasSprite sprite = quad.materialInfo().sprite();
            SpriteContents contents = sprite.contents();
            quads.add(new ShardedModel.CapturedQuad(quad, inGrid, outward, opacityOf(sprite, contents)));
            noteSprite(inGrid, contents);
        }
    }

    /**
     * Keeps the texel count of the largest quad's sprite, the face that sets the model's pixels.
     *
     * @param inGrid   the quad's vertices
     * @param contents its sprite's pixels, or null when the sprite carries none
     */
    private void noteSprite(List<QuadRectClipper.ClipVertex> inGrid, SpriteContents contents) {
        if (contents == null) {
            return;
        }
        QuadRectClipper.Rect extent = QuadRectClipper.extentOf(inGrid);
        float area = (extent.maxX() - extent.minX()) * (extent.maxY() - extent.minY());
        if (area > largestArea) {
            largestArea = area;
            texelsPerSpan = Math.max(contents.width(), contents.height());
        }
    }

    /**
     * Reads a cell's opacity from the sprite pixel under its UV.
     *
     * @param sprite   the quad's sprite
     * @param contents its pixels, or null when the sprite carries none
     * @return the cells' opacity
     */
    private static ShardedModel.CellOpacity opacityOf(TextureAtlasSprite sprite, SpriteContents contents) {
        if (contents == null) {
            return ShardedModel.CellOpacity.ALL;
        }
        return (u, v) -> !contents.isTransparent(0, pixelOf(u, sprite.getU0(), sprite.getU1(), contents.width()),
                pixelOf(v, sprite.getV0(), sprite.getV1(), contents.height()));
    }

    private static int pixelOf(float coordinate, float low, float high, int pixels) {
        float fraction = high == low ? 0f : (coordinate - low) / (high - low);
        return Math.clamp((int) Math.floor(fraction * pixels), 0, pixels - 1);
    }

    /**
     * Drops custom geometry; only baked quads break into shards.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        // A special model draws no baked quads to break (decision tiles-of-the-items-image).
    }
}

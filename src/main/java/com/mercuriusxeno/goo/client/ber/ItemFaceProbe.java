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
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an item's face the way its layers submit it, before any shard draws: the face quad's
 * sprite answers how many texels the face spans and which of them show the item, read from
 * the quads rather than assumed 16 by 16, so larger textures break along their own pixels
 * (decision tiles-of-the-items-image).
 */
final class ItemFaceProbe extends ItemQuadCollector {

    /** The texels a face spans along each side when no face quad names its sprite. */
    static final int DEFAULT_TEXELS = 16;
    /** The extent under which a quad counts as flat along an axis. */
    private static final float FLAT = 1e-4f;
    private static final float HALF = 0.5f;
    /** A face corner's index bit for lying on the high X side. */
    private static final int HIGH_X = 1;
    /** A face corner's index bit for lying on the high Y side. */
    private static final int HIGH_Y = 2;

    /** The face quads the layers submit, in the space the model's bounding box measures. */
    private final List<FaceQuad> faceQuads = new ArrayList<>();

    private ItemFaceProbe() {
        super(null);
    }

    /**
     * One quad spanning the face's X and Y, flat in Z.
     *
     * @param vertices its vertices in the face's space
     * @param sprite   its sprite
     * @param area     its extent in X times its extent in Y
     */
    private record FaceQuad(List<QuadRectClipper.ClipVertex> vertices, TextureAtlasSprite sprite, float area) {
    }

    /**
     * Probes an item and breaks its face by its seed.
     *
     * @param item the resolved item model
     * @param seed the item's seed
     * @return the face in the space the model's bounding box measures, with its shard map
     */
    static ShardFace probe(ItemStackRenderState item, long seed) {
        AABB box = item.getModelBoundingBox();
        QuadRectClipper.Rect bounds = new QuadRectClipper.Rect((float) box.minX, (float) box.minY,
                (float) box.maxX, (float) box.maxY);
        ItemFaceProbe probe = new ItemFaceProbe();
        item.submit(new PoseStack(), probe, 0, OverlayTexture.NO_OVERLAY, 0);
        FaceQuad face = probe.largestFace();
        SpriteContents contents = face == null ? null : face.sprite().contents();
        if (face == null || contents == null) {
            return new ShardFace(bounds,
                    ItemShardCutter.cut(seed, DEFAULT_TEXELS, DEFAULT_TEXELS, ItemShardCutter.TexelOpacity.ALL));
        }
        return new ShardFace(bounds, ItemShardCutter.cut(seed, contents.width(), contents.height(),
                opacityOf(face, contents, bounds)));
    }

    /**
     * Reads which of the face's texels show the item: each texel's center maps through the
     * face quad's UV onto its sprite's pixel, which answers whether it is transparent.
     *
     * @param face     the face quad
     * @param contents its sprite's pixels
     * @param bounds   the face's rectangle
     * @return the texels' opacity
     */
    private static ItemShardCutter.TexelOpacity opacityOf(FaceQuad face, SpriteContents contents,
                                                          QuadRectClipper.Rect bounds) {
        int columns = contents.width();
        int rows = contents.height();
        TextureAtlasSprite sprite = face.sprite();
        return (column, row) -> {
            float x = bounds.minX() + (column + HALF) / columns * (bounds.maxX() - bounds.minX());
            float y = bounds.minY() + (row + HALF) / rows * (bounds.maxY() - bounds.minY());
            float[] uv = uvAt(face.vertices(), x, y);
            int pixelX = pixelOf(uv[0], sprite.getU0(), sprite.getU1(), columns);
            int pixelY = pixelOf(uv[1], sprite.getV0(), sprite.getV1(), rows);
            return !contents.isTransparent(0, pixelX, pixelY);
        };
    }

    private static int pixelOf(float coordinate, float low, float high, int pixels) {
        float fraction = high == low ? 0f : (coordinate - low) / (high - low);
        return Math.clamp((int) Math.floor(fraction * pixels), 0, pixels - 1);
    }

    /**
     * Interpolates a face quad's UV at a point, bilinear between its four corners.
     *
     * @param vertices the face quad's vertices, an axis-aligned rectangle in XY
     * @param x        the X
     * @param y        the Y
     * @return the U and V there
     */
    private static float[] uvAt(List<QuadRectClipper.ClipVertex> vertices, float x, float y) {
        QuadRectClipper.Rect extent = QuadRectClipper.extentOf(vertices);
        float centerX = (extent.minX() + extent.maxX()) * HALF;
        float centerY = (extent.minY() + extent.maxY()) * HALF;
        QuadRectClipper.ClipVertex[] corners = new QuadRectClipper.ClipVertex[BakedQuad.VERTEX_COUNT];
        for (QuadRectClipper.ClipVertex vertex : vertices) {
            int corner = (vertex.x() > centerX ? HIGH_X : 0) + (vertex.y() > centerY ? HIGH_Y : 0);
            corners[corner] = vertex;
        }
        float tx = spanFraction(x, extent.minX(), extent.maxX());
        float ty = spanFraction(y, extent.minY(), extent.maxY());
        return new float[] {
            bilerp(corners[0].u(), corners[HIGH_X].u(), corners[HIGH_Y].u(), corners[HIGH_X + HIGH_Y].u(), tx, ty),
            bilerp(corners[0].v(), corners[HIGH_X].v(), corners[HIGH_Y].v(), corners[HIGH_X + HIGH_Y].v(), tx, ty),
        };
    }

    private static float spanFraction(float coordinate, float low, float high) {
        return high == low ? 0f : (coordinate - low) / (high - low);
    }

    private static float bilerp(float lowLow, float highLow, float lowHigh, float highHigh, float tx, float ty) {
        float bottom = lowLow + (highLow - lowLow) * tx;
        float top = lowHigh + (highHigh - lowHigh) * tx;
        return bottom + (top - bottom) * ty;
    }

    private @Nullable FaceQuad largestFace() {
        FaceQuad largest = null;
        for (FaceQuad face : faceQuads) {
            if (largest == null || face.area() > largest.area()) {
                largest = face;
            }
        }
        return largest;
    }

    /**
     * Records the layer's quads that span the face, in the space the bounding box measures.
     *
     * @param poseStack      the pose stack, the layer's own transforms applied
     * @param displayContext the item display context
     * @param lightCoords    packed light coordinates (unused)
     * @param overlayCoords  packed overlay coordinates (unused)
     * @param outlineColor   outline color (unused)
     * @param tintLayers     per-tint-index colors (unused)
     * @param quads          the baked quads
     * @param foilType       the foil type (unused)
     */
    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                           int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                           ItemStackRenderState.FoilType foilType) {
        for (BakedQuad quad : quads) {
            List<QuadRectClipper.ClipVertex> vertices = QuadRectClipper.verticesOf(quad).stream()
                    .map(vertex -> vertex.moved(poseStack.last().pose())).toList();
            QuadRectClipper.Rect extent = QuadRectClipper.extentOf(vertices);
            float width = extent.maxX() - extent.minX();
            float height = extent.maxY() - extent.minY();
            double depth = vertices.stream().mapToDouble(QuadRectClipper.ClipVertex::z).max().orElse(0)
                    - vertices.stream().mapToDouble(QuadRectClipper.ClipVertex::z).min().orElse(0);
            if (width > FLAT && height > FLAT && depth < FLAT) {
                faceQuads.add(new FaceQuad(vertices, quad.materialInfo().sprite(), width * height));
            }
        }
    }

    /**
     * Drops custom geometry; only baked quads carry a face.
     */
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                     SubmitNodeCollector.CustomGeometryRenderer renderer) {
        // A special model draws no baked face to break (decision tiles-of-the-items-image).
    }
}

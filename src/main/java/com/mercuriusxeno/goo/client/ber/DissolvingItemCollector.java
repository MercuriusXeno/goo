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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxy SubmitNodeCollector that re-emits one shard of a melting item's quads on the dissolve
 * render type once per glow layer, largest type first, each vertex carrying the
 * dissolve fraction, the layer's color, share and index (decisions dissolve-shader-on-item,
 * glow-color-from-mingling). The layers emit in order within one submission, so a later
 * layer's glow draws over an earlier one's and each fragment ends in one type. Each quad cuts
 * to the runs of texel cells the shard owns in it, on every face of the model (decision
 * tiles-of-the-items-image); with no sharded model the item draws whole.
 */
class DissolvingItemCollector extends ItemQuadCollector {

    private final DissolveGlow glow;
    private final @Nullable ShardedModel model;
    private final int shard;
    private final Matrix4fc shardPose;

    /**
     * Creates a proxy that dissolves the one shard of the item it is handed.
     *
     * @param delegate  the real collector to emit the dissolving geometry into
     * @param glow      how far the item has dissolved and the layers its edge glows in
     * @param model     the item's model broken into shards, or null to draw the item whole
     * @param shard     the shard this proxy emits
     * @param shardPose the pose the item is submitted at, before its layers apply their
     *                  own transforms
     */
    DissolvingItemCollector(SubmitNodeCollector delegate, DissolveGlow glow, @Nullable ShardedModel model,
                            int shard, Matrix4fc shardPose) {
        super(delegate);
        this.glow = glow;
        this.model = model;
        this.shard = shard;
        this.shardPose = new Matrix4f(shardPose);
    }

    /**
     * Re-emits the shard's pieces of the item's quads on the dissolve render type, once per
     * glow layer.
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
        GridSpace space = GridSpace.between(shardPose, poseStack.last().pose());
        resubmitByAtlas(poseStack, quads, GooRenderTypes::crucibleDissolve, (pose, buffer, group) -> {
            for (DissolveGlow.Layer layer : glow.layers()) {
                int overlay = glow.overlayCoords(layer);
                int light = DissolveGlow.lightCoords(lightCoords, layer);
                for (BakedQuad quad : group) {
                    emitShardPieces(pose, buffer, quad, space,
                            new QuadCoords(tintOf(quad, tintLayers), overlay, light));
                }
            }
        });
    }

    /**
     * The map between a layer's raw quad positions and the space the model's bounding box
     * measures, which the face's texels are laid in: the layer's own transforms, found as
     * the submitted pose with the shard's pose undone.
     *
     * @param toGrid   maps a raw quad position into the face's space
     * @param fromGrid maps a face-space position back to the raw quad's space
     */
    private record GridSpace(Matrix4fc toGrid, Matrix4fc fromGrid) {

        static GridSpace between(Matrix4fc shardPose, Matrix4fc layerPose) {
            Matrix4f toGrid = new Matrix4f(shardPose).invert().mul(layerPose);
            return new GridSpace(toGrid, new Matrix4f(toGrid).invert());
        }
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
     * Emits the pieces of one quad this proxy's shard holds with the coordinates given
     * verbatim, where putBakedQuad would fold a quad's light emission into the lightmap
     * coordinates the layer's share rides in.
     *
     * @param pose   the pose the geometry was submitted at
     * @param buffer the buffer to emit into
     * @param quad   the baked quad
     * @param space  the map between the quad's positions and the face's space
     * @param coords the tint, overlay and lightmap coordinates
     */
    private void emitShardPieces(PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, GridSpace space,
                                 QuadCoords coords) {
        Vector3f normal = pose.transformNormal(quad.direction().getUnitVec3f(), new Vector3f());
        List<QuadRectClipper.ClipVertex> inGrid = QuadRectClipper.verticesOf(quad).stream()
                .map(vertex -> vertex.moved(space.toGrid())).toList();
        for (List<QuadRectClipper.ClipVertex> piece : piecesOf(quad, inGrid)) {
            for (QuadRectClipper.ClipVertex gridVertex : piece) {
                QuadRectClipper.ClipVertex vertex = gridVertex.moved(space.fromGrid());
                Vector3f position = pose.pose().transformPosition(vertex.x(), vertex.y(), vertex.z(), new Vector3f());
                buffer.addVertex(position.x(), position.y(), position.z(),
                        ARGB.multiply(coords.tint(), vertex.color()), vertex.u(), vertex.v(),
                        coords.overlay(), coords.light(), normal.x(), normal.y(), normal.z());
            }
        }
    }

    /**
     * Returns the pieces of one quad this shard draws: the quad cut to each run of cells the
     * shard owns in it, or the whole quad when the item draws whole.
     *
     * @param quad   the baked quad
     * @param inGrid its vertices in the space the bounding box measures
     * @return the pieces, four vertices each
     */
    private List<List<QuadRectClipper.ClipVertex>> piecesOf(BakedQuad quad, List<QuadRectClipper.ClipVertex> inGrid) {
        if (model == null) {
            return List.of(inGrid);
        }
        ShardedModel.QuadCut cut = model.cutOf(quad);
        if (cut == null) {
            return List.of();
        }
        List<List<QuadRectClipper.ClipVertex>> pieces = new ArrayList<>();
        for (QuadRectClipper.Rect run : cut.runs(shard)) {
            pieces.addAll(QuadRectClipper.clip(inGrid, run, cut.axisA(), cut.axisB()));
        }
        return pieces;
    }
}

package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders goo-colored translucent fill and wireframe edges tracing a block's
 * voxel outline shape. Stairs, slabs, fences, etc. highlight their actual
 * geometry instead of a flat face quad.
 */
final class VoxelHighlightRenderer {
    /** Face highlight alpha (translucent enough to see texture beneath). */
    private static final int FACE_ALPHA = 0x1A;

    /** Wireframe outline alpha for block face edges. */
    private static final int WIRE_ALPHA = 200;

    /** Offset from the block face to prevent z-fighting. */
    private static final double FACE_OFFSET = 0.005;

    private VoxelHighlightRenderer() {}

    /**
     * Renders goo-colored translucent fill and wireframe edges tracing the
     * block's full voxel shape, so stairs, slabs and fences highlight their
     * actual geometry.
     *
     * @param poseStack    the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera       the render camera
     * @param pos          the block position
     * @param type         the goo type
     */
    static void renderBlockShape(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        Minecraft mc = Minecraft.getInstance();
        VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
        if (shape.isEmpty()) { return; }
        Vec3 offset = cameraOffset(pos, camera);
        int highlightRgb = ClientGooTypes.highlight(type);
        int edgeRgb = ClientGooTypes.edge(type);
        emitFillBoxes(poseStack, bufferSource, shape, offset.x, offset.y, offset.z, highlightRgb);
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset.x, offset.y, offset.z, edgeRgb);
    }

    /**
     * Renders a full 1x1x1 cube highlight at the given position.
     * Used for water blocks whose VoxelShape is empty.
     *
     * @param poseStack    the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera       the render camera
     * @param pos          the block position
     * @param type         the goo type
     */
    static void renderFullCube(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 offset = cameraOffset(pos, camera);
        int rgb = ClientGooTypes.highlight(type);
        int fillColor = ARGB.color(FACE_ALPHA, rgb);
        CuboidBounds box = new CuboidBounds(
                offsetMin(offset.x, 0), offsetMax(offset.x, 1),
                offsetMin(offset.z, 0), offsetMax(offset.z, 1),
                offsetMin(offset.y, 0), offsetMax(offset.y, 1));
        FlatQuadContext ctx = new FlatQuadContext(poseStack.last(),
                bufferSource.getBuffer(RenderTypes.debugQuads()));
        ctx.emitBox(fillColor, box);
        bufferSource.endLastBatch();

        int wireColor = ARGB.color(WIRE_ALPHA, ClientGooTypes.edge(type));
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext lineCtx = new LineContext(poseStack.last(),
                bufferSource.getBuffer(RenderTypes.lines()));
        lineCtx.emitWireframe(unitCubeAt(offset), wireColor, lineWidth);
        bufferSource.endLastBatch();
    }

    /**
     * The unit cube at a camera-relative offset.
     *
     * @param offset the camera-relative offset of the block's minimum corner
     * @return the cube's bounds
     */
    private static CuboidBounds unitCubeAt(Vec3 offset) {
        return new CuboidBounds(
                (float) offset.x, (float) (offset.x + 1),
                (float) offset.z, (float) (offset.z + 1),
                (float) offset.y, (float) (offset.y + 1));
    }

    /**
     * Computes the camera-relative offset for a block position.
     *
     * @param pos    the block position
     * @param camera the render camera
     * @return the camera-relative offset vector
     */
    private static Vec3 cameraOffset(BlockPos pos, Camera camera) {
        return new Vec3(
                pos.getX() - camera.position().x,
                pos.getY() - camera.position().y,
                pos.getZ() - camera.position().z);
    }

    /**
     * Emits a single translucent fill at the shape's bounding AABB.
     * Iterating each sub-box (via {@code forAllBoxes}) on a composite
     * shape draws shared internal faces twice with translucent overlap,
     * which reads as visible "seams" between segments. The bounding
     * box gives a single unified fill; the wireframe pass below still
     * traces the actual outline so cutaways stay visible.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitFillBoxes(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            VoxelShape shape, double ox, double oy, double oz, int rgb) {
        int fillColor = ARGB.color(FACE_ALPHA, rgb);
        FlatQuadContext ctx = new FlatQuadContext(poseStack.last(),
            bufferSource.getBuffer(RenderTypes.debugQuads()));
        AABB bounds = shape.bounds();
        ctx.emitBox(fillColor, new CuboidBounds(
            offsetMin(ox, bounds.minX), offsetMax(ox, bounds.maxX),
            offsetMin(oz, bounds.minZ), offsetMax(oz, bounds.maxZ),
            offsetMin(oy, bounds.minY), offsetMax(oy, bounds.maxY)));
        bufferSource.endLastBatch();
    }

    /**
     * Computes a camera-relative coordinate with inward face offset for the minimum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMin(double camOffset, double coord) {
        return (float) (camOffset + coord - FACE_OFFSET);
    }

    /**
     * Computes a camera-relative coordinate with outward face offset for the maximum bound.
     *
     * @param camOffset camera-relative offset for this axis
     * @param coord     shape-local coordinate
     * @return the offset float coordinate
     */
    private static float offsetMax(double camOffset, double coord) {
        return (float) (camOffset + coord + FACE_OFFSET);
    }

    /**
     * Emits wireframe edges along the shape outline.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param mc           the Minecraft instance
     * @param shape        the block's voxel shape
     * @param ox           camera-relative X offset
     * @param oy           camera-relative Y offset
     * @param oz           camera-relative Z offset
     * @param rgb          the RGB color value
     */
    private static void emitWireframeEdges(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Minecraft mc, VoxelShape shape,
            double ox, double oy, double oz, int rgb) {
        int wireColor = ARGB.color(WIRE_ALPHA, rgb);
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(RenderTypes.lines()));
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
            ctx.emitEdge(
                (float) (ox + x0), (float) (oy + y0), (float) (oz + z0),
                (float) (ox + x1), (float) (oy + y1), (float) (oz + z1),
                wireColor, lineWidth));
        bufferSource.endLastBatch();
    }

}

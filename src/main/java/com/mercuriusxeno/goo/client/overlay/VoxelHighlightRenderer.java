package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders goo-colored wireframe edges tracing a block's voxel outline shape,
 * with no fill over the block, so a transmuting block stays readable beneath
 * the aim. Stairs, slabs, fences, etc. outline their actual geometry.
 * block-highlight-is-outline-and-ripple
 */
final class VoxelHighlightRenderer {
    /** Wireframe outline alpha for block face edges. */
    private static final int WIRE_ALPHA = 200;

    private VoxelHighlightRenderer() {}

    /**
     * Renders goo-colored wireframe edges tracing the block's full voxel
     * shape, so stairs, slabs and fences outline their actual geometry.
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
        emitWireframeEdges(poseStack, bufferSource, mc, shape, offset, ClientGooTypes.edge(type), RenderTypes.lines());
    }

    /**
     * Renders a full 1x1x1 cube outline at the given position.
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
     * Emits wireframe edges along the shape outline.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param mc           the Minecraft instance
     * @param shape        the block's voxel shape
     * @param offset       the block's camera-relative offset
     * @param rgb          the RGB color value
     * @param lines        the lines render type, depth tested or seen through walls
     */
    private static void emitWireframeEdges(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Minecraft mc, VoxelShape shape, Vec3 offset, int rgb, RenderType lines) {
        int wireColor = ARGB.color(WIRE_ALPHA, rgb);
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(lines));
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) ->
            ctx.emitEdge(
                (float) (offset.x + x0), (float) (offset.y + y0), (float) (offset.z + z0),
                (float) (offset.x + x1), (float) (offset.y + y1), (float) (offset.z + z1),
                wireColor, lineWidth));
        bufferSource.endLastBatch();
    }

}

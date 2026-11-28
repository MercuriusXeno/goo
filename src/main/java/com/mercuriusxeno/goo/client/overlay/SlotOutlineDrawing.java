package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Low-level outline and wireframe drawing routines for slot outline rendering.
 * Extracted from SlotOutlineRenderer to keep method counts under threshold.
 * Minecraft 26.2 hands an outline renderer the frame's submit collector, once,
 * in place of 26.1's buffer source called on the opaque and translucent passes
 * apart: the outline submits as vanilla's does, after the translucent terrain
 * where the target block is translucent.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
final class SlotOutlineDrawing {

    /** Translucent green color for placement preview wireframe. */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /** Translucent red color for pickup highlight wireframe. */
    private static final int PICKUP_COLOR = ARGB.color(180, 255, 80, 80);

    /** Opaque black outline color for high-contrast secondary outline. */
    private static final int HC_SECONDARY_COLOR = -16_777_216;

    /** Line width for high-contrast secondary outline. */
    private static final float HC_SECONDARY_WIDTH = 7.0F;

    /** High-contrast primary outline color. */
    private static final int HC_PRIMARY_COLOR = -11_010_079;

    /** Black alpha for standard outline. */
    private static final int OUTLINE_BLACK_ALPHA = 102;

    private SlotOutlineDrawing() { }

    /**
     * Renders a custom outline shape using the same style as vanilla block outlines.
     * If a preview AABB is provided, also draws a wireframe cuboid for placement preview.
     *
     * @return true to suppress vanilla outline rendering
     *
     * @param renderState      the block outline render state
     * @param collector        the frame's submit collector
     * @param poseStack        the pose stack for rendering
     * @param levelRenderState the level render state
     * @param shape            the voxel shape to render
     * @param preview          the placement preview bounds, or null
     */
    static boolean renderOutline(BlockOutlineRenderState renderState, SubmitNodeCollector collector,
            PoseStack poseStack, LevelRenderState levelRenderState, VoxelShape shape, @Nullable AABB preview) {
        return renderOutline(renderState, collector, poseStack, levelRenderState, shape, preview, null);
    }

    /**
     * Renders outline with optional green preview and red pickup highlight.
     *
     * @param renderState      the block outline render state
     * @param collector        the frame's submit collector
     * @param poseStack        the pose stack for rendering
     * @param levelRenderState the level render state
     * @param shape            the voxel shape to render
     * @param preview          the placement preview bounds, or null
     * @param pickup           the pickup highlight bounds, or null
     * @return true to suppress vanilla outline rendering
     */
    static boolean renderOutline(BlockOutlineRenderState renderState, SubmitNodeCollector collector,
            PoseStack poseStack, LevelRenderState levelRenderState, VoxelShape shape, @Nullable AABB preview,
            @Nullable AABB pickup) {
        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        BlockPos pos = renderState.pos();
        renderSelectionOutline(renderState, collector, poseStack, shape, pos, camPos);
        renderOptionalPreview(poseStack, collector, preview, pos, camPos);
        if (pickup != null) {
            renderCameraRelativeWireframe(poseStack, collector, pickup, pos, camPos, PICKUP_COLOR);
        }
        return true;
    }

    /**
     * Renders the placement preview wireframe if a preview AABB is present.
     *
     * @param poseStack the pose stack for rendering
     * @param collector the frame's submit collector
     * @param preview   the placement preview bounds, or null
     * @param pos       the block position
     * @param camPos    the camera world position
     */
    static void renderOptionalPreview(PoseStack poseStack, SubmitNodeCollector collector,
            @Nullable AABB preview, BlockPos pos, Vec3 camPos) {
        if (preview != null) {
            renderPreviewWireframe(poseStack, collector, preview, pos, camPos);
        }
    }

    /**
     * Renders the standard selection outline (with high-contrast support).
     *
     * @param renderState the block outline render state
     * @param collector   the frame's submit collector
     * @param poseStack   the pose stack for rendering
     * @param shape       the voxel shape to render
     * @param pos         the block position
     * @param camPos      the camera world position
     */
    static void renderSelectionOutline(BlockOutlineRenderState renderState, SubmitNodeCollector collector,
            PoseStack poseStack, VoxelShape shape, BlockPos pos, Vec3 camPos) {
        boolean afterTerrain = renderState.isTranslucent();
        if (renderState.highContrast()) {
            renderShapeOutline(poseStack, collector, RenderTypes.secondaryBlockOutline(),
                    shape, pos, camPos, HC_SECONDARY_COLOR, HC_SECONDARY_WIDTH, afterTerrain);
        }
        int color = renderState.highContrast() ? HC_PRIMARY_COLOR : ARGB.black(OUTLINE_BLACK_ALPHA);
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        renderShapeOutline(poseStack, collector,
                selectionOutlineType(renderState.highContrast(),
                        Minecraft.getInstance().gameRenderer.useImprovedTransparency()),
                shape, pos, camPos, color, lineWidth, afterTerrain);
    }

    /**
     * The line type a selection outline draws through, as vanilla 26.3 picks its own block outline's: a
     * translucent outline draws in the OIT phase under improved transparency, so its type there carries an OIT
     * set (defect row 8).
     * decision goo-translucency-draws-through-order-independent-transparency
     *
     * @param highContrast         whether the high-contrast outline is on
     * @param improvedTransparency whether the client draws translucency through order-independent transparency
     * @return the outline's render type
     */
    static RenderType selectionOutlineType(boolean highContrast, boolean improvedTransparency) {
        if (highContrast) {
            return RenderTypes.linesDepthBias();
        }
        return improvedTransparency ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
    }

    /**
     * Renders a wireframe cuboid offset to camera-relative coordinates.
     *
     * @param poseStack the pose stack for rendering
     * @param collector the frame's submit collector
     * @param bounds    the axis-aligned bounding box
     * @param pos       the block position
     * @param camPos    the camera world position
     * @param color     the ARGB color value
     */
    private static void renderCameraRelativeWireframe(PoseStack poseStack, SubmitNodeCollector collector,
            AABB bounds, BlockPos pos, Vec3 camPos, int color) {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        AABB offset = bounds.move(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
        CuboidBounds cuboid = new CuboidBounds(
            (float) offset.minX, (float) offset.maxX,
            (float) offset.minZ, (float) offset.maxZ,
            (float) offset.minY, (float) offset.maxY);
        collector.submitCustomGeometry(poseStack,
                wireframeType(Minecraft.getInstance().gameRenderer.useImprovedTransparency()),
                (pose, consumer) -> new LineContext(pose, consumer).emitWireframe(cuboid, color, lineWidth));
    }

    /**
     * The line type a slot's placement preview and removal indicator draw through. The indicator sits on a
     * canister's own edges, so under improved transparency it draws in the OIT phase beside the canister's
     * translucent faces and blends with them, where opaque lines written from the solid phase z-fight them.
     * decision goo-translucency-draws-through-order-independent-transparency
     *
     * @param improvedTransparency whether the client draws translucency through order-independent transparency
     * @return the wireframe's render type
     */
    static RenderType wireframeType(boolean improvedTransparency) {
        return improvedTransparency ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.lines();
    }

    /**
     * Renders a wireframe placement preview for an empty slot.
     *
     * @param poseStack the pose stack for rendering
     * @param collector the frame's submit collector
     * @param bounds    the axis-aligned bounding box
     * @param pos       the block position
     * @param camPos    the camera world position
     */
    static void renderPreviewWireframe(PoseStack poseStack, SubmitNodeCollector collector,
            AABB bounds, BlockPos pos, Vec3 camPos) {
        renderCameraRelativeWireframe(poseStack, collector, bounds, pos, camPos, PREVIEW_COLOR);
    }

    /**
     * Submits a shape's outline at the block, camera relative, as vanilla submits a block's.
     *
     * @param poseStack    the pose stack for rendering
     * @param collector    the frame's submit collector
     * @param renderType   the render type for lines
     * @param shape        the voxel shape to render
     * @param pos          the block position
     * @param camPos       the camera world position
     * @param color        the ARGB color value
     * @param lineWidth    the line width in pixels
     * @param afterTerrain whether it draws after the translucent terrain, as a translucent block's outline does
     */
    static void renderShapeOutline(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
            VoxelShape shape, BlockPos pos, Vec3 camPos, int color, float lineWidth, boolean afterTerrain) {
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
        collector.submitShapeOutline(poseStack, shape, renderType, color, lineWidth, afterTerrain);
        poseStack.popPose();
    }

}

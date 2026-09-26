package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.ability.FuseOrbVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The floating panel above an aimed-at chain marker showing its stack
 * count (e.g. "3 / 28") beside the goo type icon, clearing the orb by the
 * size FuseOrbVisual draws it at (decision render-context-is-the-one-emitter).
 */
public final class ChainMarkerBillboard {

    /** Gap between the orb and the billboard. */
    private static final float BILLBOARD_GAP = 0.15f;
    /** Padding inside the nine-slice background. */
    private static final float BILLBOARD_PADDING = 4f;
    /** Padding counted on both sides (left+right or top+bottom). */
    private static final int PADDING_BOTH_SIDES = 2;
    /** Divisor to halve a dimension for centering. */
    private static final float HALF_DIVISOR = 2f;
    /** Half block offset from a face to the block center. */
    private static final double FACE_CENTER_OFFSET = 0.5;
    /** Separator between stack count and max stacks. */
    private static final String STACK_SEPARATOR = " / ";

    private ChainMarkerBillboard() {
    }

    /**
     * Renders the billboard above the chain marker at the given position.
     *
     * @param ps      the pose stack
     * @param buf     the buffer source
     * @param camera  the render camera
     * @param level   the client level
     * @param font    the font renderer
     * @param pos     the chain marker block position
     * @param gooType the goo type for the icon
     */
    public static void render(PoseStack ps, MultiBufferSource.BufferSource buf, Camera camera,
                              Level level, Font font, BlockPos pos, ResourceKey<GooTypeDefinition> gooType) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return;
        }
        PanelRow row = PanelPainter.gooRow(gooType, be.getStackCount() + STACK_SEPARATOR + be.getMaxStacks());
        float panelW = row.width(font::width) + BILLBOARD_PADDING * PADDING_BOTH_SIDES;
        float panelH = PanelPainter.ROW_HEIGHT + BILLBOARD_PADDING * PADDING_BOTH_SIDES;
        positionBillboard(ps, camera, pos, be);
        renderContent(ps, buf, font, row, panelW, panelH);
        ps.popPose();
    }

    /**
     * Translates and rotates the pose stack to put the billboard beside the
     * orb, above it or below it as the camera sits.
     *
     * @param ps     the pose stack
     * @param camera the render camera
     * @param pos    the chain marker block position
     * @param be     the chain marker block entity
     */
    private static void positionBillboard(PoseStack ps, Camera camera, BlockPos pos, ChainMarkerBlockEntity be) {
        float orbRadius = FuseOrbVisual.peakShellHalf(be.getStackCount());
        Direction face = be.getPlacedFace();
        Vec3 orbCenter = new Vec3(
                pos.getX() + FACE_CENTER_OFFSET - face.getStepX() * FACE_CENTER_OFFSET,
                pos.getY() + FACE_CENTER_OFFSET - face.getStepY() * FACE_CENTER_OFFSET,
                pos.getZ() + FACE_CENTER_OFFSET - face.getStepZ() * FACE_CENTER_OFFSET);
        Vec3 cam = camera.position();
        double billboardY = cam.y < orbCenter.y
                ? orbCenter.y - orbRadius - BILLBOARD_GAP
                : orbCenter.y + orbRadius + BILLBOARD_GAP;
        double pullForward = orbRadius + BILLBOARD_GAP;
        ps.pushPose();
        ps.translate(
                orbCenter.x + face.getStepX() * pullForward - cam.x,
                billboardY - cam.y,
                orbCenter.z + face.getStepZ() * pullForward - cam.z);
        InWorldHud.applyBillboardRotation(ps, camera, 1f);
        ps.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }

    /**
     * Renders the billboard background panel and goo row text.
     *
     * @param ps     the pose stack
     * @param buf    the buffer source
     * @param font   the font renderer
     * @param row    the goo row to display
     * @param panelW the panel width
     * @param panelH the panel height
     */
    private static void renderContent(PoseStack ps, MultiBufferSource.BufferSource buf,
                                      Font font, PanelRow row, float panelW, float panelH) {
        float halfW = panelW / HALF_DIVISOR;
        float halfH = panelH / HALF_DIVISOR;
        InWorldHud.renderBackgroundSeeThrough(ps, buf, new PanelRectangle(-halfW, -halfH, panelW, panelH));
        PanelPainter.drawRow(ps, font, buf, row, -halfW + BILLBOARD_PADDING, -halfH + BILLBOARD_PADDING);
    }
}

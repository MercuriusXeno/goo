package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.LineContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Draws Spire's preview in place of a wireframe: the ripple echo, the face
 * bullseye's RippleRings grown across the footprint, each ring a glowing
 * rectangle scaled from the footprint's center out to its edge in the goo
 * type's color, fading as it grows, over a steady outline of that edge; and, once the footprint is fixed and the
 * rise is being set, ghost stone, the stone Spire grows drawn translucent and
 * full bright through the moving-block shader, only its outer faces, so it
 * reads as one solid ghost with no seams between its blocks.
 * decision spire-previews-as-a-surface-ripple-and-ghost-stone
 */
final class SpirePreviewRenderer {

    /** A ring's alpha at its birth, out of 255, the face bullseye's own. */
    static final int RING_PEAK_ALPHA = 160;
    /** The steady edge outline's alpha, out of 255, solid enough to read between rings. */
    static final int EDGE_ALPHA = 230;
    /** The ghost stone's alpha, out of 255. */
    static final int GHOST_ALPHA = 150;
    /** How far the ghost stone leans toward the goo type's color, 0 for plain stone. */
    static final float GHOST_TINT = 0.35f;
    /** The stone Spire grows, which the ghost draws. */
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final float WHITE = 255f;

    private SpirePreviewRenderer() {
    }

    /**
     * Draws the preview for the live footprint.
     *
     * @param ps         the pose stack
     * @param buf        the buffer source
     * @param camPos     the camera position
     * @param footprint  the live footprint
     * @param rising     whether the footprint is fixed and the rise is being set
     * @param rgb        the goo type's highlight color
     * @param nowSeconds the real-time clock the rings run on
     */
    static void render(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos, SpireFootprint footprint,
                       boolean rising, int rgb, double nowSeconds) {
        drawRings(ps, buf, camPos, footprint, rgb, nowSeconds);
        if (rising) {
            drawGhostStone(Minecraft.getInstance(), ps, buf, camPos, SpirePreview.ghostCells(footprint), rgb);
        }
    }

    private static void drawRings(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos,
                                  SpireFootprint footprint, int rgb, double nowSeconds) {
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext lines = new LineContext(ps.last(), buf.getBuffer(GooRenderTypes.LINES_GLOW));
        lines.emitPolyline(camPos, SpirePreview.edgeOutline(footprint), ARGB.color(EDGE_ALPHA, rgb), width);
        for (double phase : RippleRings.ringPhases(nowSeconds)) {
            int alpha = (int) (RING_PEAK_ALPHA * RippleRings.ringOpacity(phase));
            if (RippleRings.isAlive(phase) && alpha > 0) {
                lines.emitPolyline(camPos, SpirePreview.ringCorners(footprint, phase), ARGB.color(alpha, rgb), width);
            }
        }
        buf.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static void drawGhostStone(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos,
                                       List<BlockPos> cells, int rgb) {
        RenderType ghost = GooSubmitter.movingBlockTranslucent();
        VertexConsumer consumer = buf.getBuffer(ghost);
        QuadInstance look = new QuadInstance();
        look.setColor(ghostColor(rgb));
        look.setLightCoords(GooSubmitter.fullbrightLight());
        look.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(STONE);
        Set<BlockPos> all = Set.copyOf(cells);
        for (BlockPos cell : cells) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(mc.level, cell, STONE, RandomSource.create(STONE.getSeed(cell)), parts);
            ps.pushPose();
            ps.translate(cell.getX() - camPos.x, cell.getY() - camPos.y, cell.getZ() - camPos.z);
            for (Direction side : SpirePreview.outerFaces(cell, all)) {
                for (BlockStateModelPart part : parts) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(ps.last(), quad, look));
                }
            }
            ps.popPose();
        }
        buf.endBatch(ghost);
    }

    /**
     * The ghost stone's tint: white leaning toward the goo type's color, translucent.
     *
     * @param rgb the goo type's color
     * @return the ARGB
     */
    static int ghostColor(int rgb) {
        int red = Math.round(WHITE + (ARGB.red(rgb) - WHITE) * GHOST_TINT);
        int green = Math.round(WHITE + (ARGB.green(rgb) - WHITE) * GHOST_TINT);
        int blue = Math.round(WHITE + (ARGB.blue(rgb) - WHITE) * GHOST_TINT);
        return ARGB.color(GHOST_ALPHA, red, green, blue);
    }
}

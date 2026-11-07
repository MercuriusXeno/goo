package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.SurfaceRipple;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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

/**
 * Draws Spire's preview in place of a wireframe: a ripple shimmering across
 * the footprint's surface in the goo type's color, each ground cell's top
 * cut into a grid of quads fine enough to follow SurfaceRipple's wave, each
 * quad taking the wave's alpha at its corners so neighbouring quads blend into
 * one smooth rippling sheet rather than a checkerboard; and, once the footprint
 * is fixed and the rise is being set, ghost stone, the stone Spire grows drawn
 * translucent and full bright in every cell the rise would fill, through the
 * moving-block shader so it shades as a world block does, never twice.
 * decision spire-previews-as-a-surface-ripple-and-ghost-stone
 */
final class SpirePreviewRenderer {

    /** Quads along each edge of a ground cell's top, at least five to the ripple's shortest wave. */
    static final int RIPPLE_QUADS_ACROSS = 12;
    /** A quad's corners, in the order its vertices are drawn. */
    static final int NORTH_WEST = 0;
    static final int SOUTH_WEST = 1;
    static final int SOUTH_EAST = 2;
    static final int NORTH_EAST = 3;
    private static final int CORNERS = 4;
    /** The ripple's alpha where its wave sits level, out of 255. */
    static final int RIPPLE_ALPHA = 120;
    /** How far the wave swings the ripple's alpha either way, out of 255. */
    static final int RIPPLE_ALPHA_SWING = 70;
    /** How far above the ground the ripple floats, so it never fights the ground's own face. */
    static final float RIPPLE_LIFT = 0.02f;
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
     * @param ps        the pose stack
     * @param buf       the buffer source
     * @param camPos    the camera position
     * @param footprint the live footprint
     * @param rising    whether the footprint is fixed and the rise is being set
     * @param rgb       the goo type's highlight color
     */
    static void render(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos, SpireFootprint footprint,
                       boolean rising, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        float day = SurfaceRipple.dayFraction(mc.level.getGameTime(),
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        drawRipple(ps, buf, camPos, SpirePreview.rippleCells(footprint), day, rgb);
        if (rising) {
            drawGhostStone(mc, ps, buf, camPos, SpirePreview.ghostCells(footprint), rgb);
        }
    }

    private static void drawRipple(PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 camPos,
                                   List<BlockPos> cells, float day, int rgb) {
        VertexConsumer quads = buf.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = ps.last();
        float step = 1f / RIPPLE_QUADS_ACROSS;
        for (BlockPos cell : cells) {
            float y = (float) (cell.getY() + 1 + RIPPLE_LIFT - camPos.y);
            for (int across = 0; across < RIPPLE_QUADS_ACROSS; across++) {
                for (int down = 0; down < RIPPLE_QUADS_ACROSS; down++) {
                    double worldX = cell.getX() + across * (double) step;
                    double worldZ = cell.getZ() + down * (double) step;
                    int[] colors = quadColors(worldX, worldZ, step, day, rgb);
                    float x0 = (float) (worldX - camPos.x);
                    float z0 = (float) (worldZ - camPos.z);
                    quads.addVertex(pose, x0, y, z0).setColor(colors[NORTH_WEST]);
                    quads.addVertex(pose, x0, y, z0 + step).setColor(colors[SOUTH_WEST]);
                    quads.addVertex(pose, x0 + step, y, z0 + step).setColor(colors[SOUTH_EAST]);
                    quads.addVertex(pose, x0 + step, y, z0).setColor(colors[NORTH_EAST]);
                }
            }
        }
        buf.endBatch(RenderTypes.debugQuads());
    }

    /**
     * The ripple's colors at a quad's four corners, in the order its vertices
     * are drawn: (x0, z0), (x0, z0 + step), (x0 + step, z0 + step), (x0 + step, z0).
     *
     * @param x0   the quad's west edge, in world blocks
     * @param z0   the quad's north edge, in world blocks
     * @param step the quad's width
     * @param day  the day fraction the wave runs on
     * @param rgb  the goo type's color
     * @return the four corners' ARGB
     */
    static int[] quadColors(double x0, double z0, double step, float day, int rgb) {
        int[] colors = new int[CORNERS];
        colors[NORTH_WEST] = rippleColor(SurfaceRipple.at(x0, z0, day), rgb);
        colors[SOUTH_WEST] = rippleColor(SurfaceRipple.at(x0, z0 + step, day), rgb);
        colors[SOUTH_EAST] = rippleColor(SurfaceRipple.at(x0 + step, z0 + step, day), rgb);
        colors[NORTH_EAST] = rippleColor(SurfaceRipple.at(x0 + step, z0, day), rgb);
        return colors;
    }

    /**
     * The ripple's color at one spot: the goo type's color, its alpha riding the wave.
     *
     * @param wave the wave's height there, minus one to one
     * @param rgb  the goo type's color
     * @return the ARGB
     */
    static int rippleColor(float wave, int rgb) {
        int alpha = Math.clamp(Math.round(RIPPLE_ALPHA + RIPPLE_ALPHA_SWING * wave), 0, (int) WHITE);
        return ARGB.color(alpha, rgb);
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
        for (BlockPos cell : cells) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(mc.level, cell, STONE, RandomSource.create(STONE.getSeed(cell)), parts);
            ps.pushPose();
            ps.translate(cell.getX() - camPos.x, cell.getY() - camPos.y, cell.getZ() - camPos.z);
            for (BlockStateModelPart part : parts) {
                for (Direction side : Direction.values()) {
                    part.getQuads(side).forEach(quad -> consumer.putBakedQuad(ps.last(), quad, look));
                }
                part.getQuads(null).forEach(quad -> consumer.putBakedQuad(ps.last(), quad, look));
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

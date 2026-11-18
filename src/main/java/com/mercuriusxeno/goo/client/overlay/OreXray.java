package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shows the gem ore Glimmer revealed through walls: each ore block drawn as
 * itself, its own texture, a little translucent and fully lit, with a soft
 * crystal glow about it, fading in as the front reaches it and away at its
 * life's end, through the pipelines Sight shows fungus by.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreXray {

    /** The ore's own color, a little translucent at full show. */
    private static final int ORE_RGB = 0xFFFFFF;
    private static final float ORE_ALPHA = 0.8f;
    /** The glow's pale crystal blue. */
    private static final int GLOW_RGB = 0xA8E8FF;
    private static final float GLOW_ALPHA = 0.3f;
    /** How far the glow swells past the block. */
    private static final float GLOW_SWELL = 1.1f;
    private static final int OPAQUE = 255;

    private OreXray() {
    }

    /**
     * Hands the shared path the revealed ore, drawn through walls, sorted by
     * the nearest ore block showing.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision glitter-sphere-icons-gem-ore-groups
     *
     * @param frame the frame being drawn
     * @param draws where the ore's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<OreSightings.Sighting> showing = OreSightings.CLIENT.showingAt(now);
        if (!showing.isEmpty()) {
            draws.accept(SortedDraw.nearestOf(showing.stream().map(sighting -> Vec3.atCenterOf(sighting.pos())),
                    mc.gameRenderer.getMainCamera().position(), () -> drawOre(mc, frame.poseStack(), showing)));
        }
    }

    private static void drawOre(Minecraft mc, PoseStack poseStack, List<OreSightings.Sighting> showing) {
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        drawPass(mc, poseStack, GooRenderTypes.fungusXray(atlas), showing, ORE_RGB, ORE_ALPHA, 1f);
        drawPass(mc, poseStack, GooRenderTypes.fungusGlow(atlas), showing, GLOW_RGB, GLOW_ALPHA, GLOW_SWELL);
    }

    /**
     * Draws every showing ore block once through one pass, each as fully as
     * its vein shows.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param pass      the render type the pass draws through
     * @param showing   the ore blocks showing
     * @param rgb       the pass's color
     * @param alpha     the pass's alpha at full show
     * @param swell     the scale about each block's center
     */
    private static void drawPass(Minecraft mc, PoseStack poseStack, RenderType pass,
                                 List<OreSightings.Sighting> showing, int rgb, float alpha, float swell) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = buffers.getBuffer(pass);
        for (OreSightings.Sighting sighting : showing) {
            FungusXray.drawBlock(mc, poseStack, consumer, camera, sighting.pos(),
                    FungusXray.instance(color(rgb, alpha * sighting.shown())), swell);
        }
        buffers.endBatch(pass);
    }

    private static int color(int rgb, float alpha) {
        return ARGB.color(Math.round(Math.clamp(alpha, 0f, 1f) * OPAQUE), rgb);
    }
}

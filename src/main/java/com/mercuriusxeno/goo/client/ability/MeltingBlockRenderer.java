package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws Unmake's melt: a block's surface liquefies in place into rippling
 * unstable goo as the unmake works it, a goo skin over the block thickening
 * from clear to solid, and once the block is gone the goo collapses, slumping
 * flat and spreading as it fades.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingBlockRenderer {

    /** The goo skin's alpha once the block is fully liquefied. */
    private static final int LIQUEFIED_ALPHA = 0xE0;
    /** How far the skin stands off the block before it ripples, so it never fights the block's faces. */
    private static final float SKIN_GAP = 0.004f;
    /** How far the skin's ripple swells it at full liquefaction. */
    private static final float RIPPLE_SWELL = 0.03f;
    /** Ripple cycles a tick. */
    private static final float RIPPLE_RATE = 0.35f;
    /** How tall the goo stands once collapsed, as a share of the block. */
    private static final float PUDDLE_HEIGHT = 0.08f;
    /** How far the goo spreads past the block's footprint once collapsed, each side. */
    private static final float PUDDLE_SPREAD = 0.2f;
    private static final float WHOLE = 1f;
    private static final float HALF = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;

    private MeltingBlockRenderer() {
    }

    /**
     * Submits every melt this frame.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(GooTypes.UNSTABLE));
        for (MeltingBlocks.Melt melt : MeltingBlocks.CLIENT.melts(now, pos -> mc.level.getBlockState(pos).isAir())) {
            PoseStack poseStack = event.getPoseStack();
            Vec3 corner = Vec3.atLowerCornerOf(melt.pos()).subtract(camera.pos);
            poseStack.pushPose();
            poseStack.translate(corner.x, corner.y, corner.z);
            int color = ARGB.color(alphaOf(melt), GooRenderUtil.OPAQUE_WHITE);
            CuboidBounds bounds = boundsOf(melt, now);
            GooSubmitter.submitFluid(poseStack, event.getSubmitNodeCollector(), ctx -> ctx.emitBox(color, bounds, uv));
            poseStack.popPose();
        }
    }

    /**
     * The goo's alpha: thickening with the liquefied share while the block
     * stands, fading as it collapses.
     *
     * @param melt the melt
     * @return the alpha, 0 to 255
     */
    static int alphaOf(MeltingBlocks.Melt melt) {
        float thickness = melt.liquefied() * (WHOLE - melt.collapse());
        return Math.round(LIQUEFIED_ALPHA * thickness);
    }

    /**
     * The goo's box in block-local coordinates: a rippling skin just over the
     * block while it stands, slumping flat and spreading past its footprint
     * as it collapses.
     *
     * @param melt the melt
     * @param now  the game time including the partial tick
     * @return the box
     */
    static CuboidBounds boundsOf(MeltingBlocks.Melt melt, float now) {
        float ripple = SKIN_GAP + RIPPLE_SWELL * melt.liquefied()
                * (HALF + HALF * (float) Math.sin(now * RIPPLE_RATE * TWO_PI));
        float spread = ripple + PUDDLE_SPREAD * melt.collapse();
        float top = WHOLE + ripple - (WHOLE + ripple - PUDDLE_HEIGHT) * melt.collapse();
        return new CuboidBounds(-spread, WHOLE + spread, -spread, WHOLE + spread, 0f, top);
    }
}

package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * Draws Unmake's melt over a block or a mob: its surface liquefies in place
 * into rippling unstable goo as the unmake works it, a goo skin over it
 * thickening from clear to solid, and once it is gone the goo collapses,
 * slumping flat and spreading as it fades.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingBlockRenderer {

    /** The goo skin's alpha once fully liquefied. */
    private static final int LIQUEFIED_ALPHA = 0xE0;
    /** How far the skin stands off what it covers before it ripples, so it never fights its faces. */
    private static final double SKIN_GAP = 0.004;
    /** How far the skin's ripple swells it at full liquefaction. */
    private static final double RIPPLE_SWELL = 0.03;
    /** Ripple cycles a tick. */
    private static final double RIPPLE_RATE = 0.35;
    /** How tall the goo stands once collapsed, as a share of what it covered. */
    private static final double PUDDLE_HEIGHT = 0.08;
    /** How far the goo spreads past the footprint once collapsed, each side, as a share of its width. */
    private static final double PUDDLE_SPREAD = 0.2;
    private static final double HALF = 0.5;
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
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float now = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (MeltingBlocks.Melt melt : MeltingBlocks.CLIENT.melts(now, pos -> level.getBlockState(pos).isAir())) {
            submitMelt(event, new AABB(melt.pos()), melt.liquefied(), melt.collapse(), now);
        }
        for (MeltingMobs.Melt melt : MeltingMobs.CLIENT.melts(now, id -> bodyOf(level.getEntity(id)))) {
            submitMelt(event, melt.box(), melt.liquefied(), melt.collapse(), now);
        }
    }

    /**
     * @param entity the mob, or null once gone
     * @return its body, or null once gone
     */
    private static @Nullable AABB bodyOf(@Nullable Entity entity) {
        return entity == null || entity.isRemoved() ? null : entity.getBoundingBox();
    }

    /**
     * Submits one melt's goo over what it covers.
     *
     * @param event     the custom geometry submit event
     * @param covered   what the goo covers, in world coordinates
     * @param liquefied how much has turned to goo, 0 to 1
     * @param collapse  how far the goo has collapsed, 0 to 1
     * @param now       the game time including the partial tick
     */
    private static void submitMelt(SubmitCustomGeometryEvent event, AABB covered, float liquefied, float collapse,
                                   float now) {
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        AABB goo = gooOver(covered, liquefied, collapse, now);
        Vec3 corner = new Vec3(goo.minX, goo.minY, goo.minZ).subtract(camera.pos);
        int color = ARGB.color(alphaOf(liquefied, collapse), GooRenderUtil.OPAQUE_WHITE);
        CuboidBounds bounds = new CuboidBounds(0f, (float) goo.getXsize(), 0f, (float) goo.getZsize(), 0f,
                (float) goo.getYsize());
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(GooTypes.UNSTABLE));
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(corner.x, corner.y, corner.z);
        GooSubmitter.submitFluid(poseStack, event.getSubmitNodeCollector(), ctx -> ctx.emitBox(color, bounds, uv));
        poseStack.popPose();
    }

    /**
     * The goo's alpha: thickening as it liquefies, fading as it collapses.
     *
     * @param liquefied how much has turned to goo, 0 to 1
     * @param collapse  how far the goo has collapsed, 0 to 1
     * @return the alpha, 0 to 255
     */
    static int alphaOf(float liquefied, float collapse) {
        return (int) Math.round(LIQUEFIED_ALPHA * liquefied * (1.0 - collapse));
    }

    /**
     * The goo's box: a rippling skin just over what it covers while that
     * stands, slumping flat on its floor and spreading past its footprint as
     * it collapses.
     *
     * @param covered   what the goo covers
     * @param liquefied how much has turned to goo, 0 to 1
     * @param collapse  how far the goo has collapsed, 0 to 1
     * @param now       the game time including the partial tick
     * @return the goo's box, in world coordinates
     */
    static AABB gooOver(AABB covered, float liquefied, float collapse, float now) {
        double ripple = SKIN_GAP + RIPPLE_SWELL * liquefied * (HALF + HALF * Math.sin(now * RIPPLE_RATE * TWO_PI));
        double spreadX = ripple + PUDDLE_SPREAD * covered.getXsize() * collapse;
        double spreadZ = ripple + PUDDLE_SPREAD * covered.getZsize() * collapse;
        double standing = covered.getYsize() + ripple;
        double height = standing - (standing - PUDDLE_HEIGHT * covered.getYsize()) * collapse;
        return new AABB(covered.minX - spreadX, covered.minY, covered.minZ - spreadZ,
                covered.maxX + spreadX, covered.minY + height, covered.maxZ + spreadZ);
    }
}

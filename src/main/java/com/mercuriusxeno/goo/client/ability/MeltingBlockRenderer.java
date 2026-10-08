package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws what Unmake leaves on screen outside the melting block's own
 * renderer: the goo copy sagging in place of a melting mob, whose own body is
 * not drawn; the goo skin over a block that melts without the sag, a block
 * holding contents or one under a tap; and the remains of anything unmade
 * morphing into the goo item where the item then drops.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingBlockRenderer {

    /** The goo's alpha, translucent enough to read as goo. */
    static final int GOO_ALPHA = 0xE0;
    /** How far a skin stands off the block it covers, so it never fights the block's faces. */
    private static final float SKIN_GAP = 0.004f;
    /** How big the remains end, in blocks, the size of the goo item on the ground. */
    static final float ITEM_SIZE = 0.25f;
    /** Where in the morph the goo item starts to show through the remains. */
    static final float ITEM_SHOWS = 0.4f;
    /** How far the item entity lifts its model off its feet at rest. */
    private static final float ITEM_LIFT = 0.1f;
    /** The goo an item stack shows the morph holds, the amount only picking its model. */
    private static final int SHOWN_GOO = 1000;
    private static final float HALF = 0.5f;
    /** The cubic smoothstep's constant term. */
    private static final float SMOOTH_BASE = 3f;
    /** The cubic smoothstep's slope term. */
    private static final float SMOOTH_SLOPE = 2f;
    /** A block skin's one layer, covering the block whole with its gap either side. */
    private static final List<CuboidBounds> SKIN = List.of(new CuboidBounds(0f, 1f + 2 * SKIN_GAP, 0f,
            1f + 2 * SKIN_GAP, 0f, 1f + 2 * SKIN_GAP));

    private MeltingBlockRenderer() {
    }

    /**
     * Keeps a melting mob's own body from drawing while its goo copy stands in for it.
     *
     * @param event the living render event
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?, ?> event) {
        if (event.getRenderState().getRenderDataOrDefault(MeltingMobs.MELTING, false)) {
            event.setCanceled(true);
        }
    }

    /**
     * Submits every sagging mob, skinned block and morphing remains this frame.
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
        MeltingBlocks.CLIENT.worked(now).forEach((pos, melted) -> submitSkin(event, level, pos, melted));
        MeltingMobs.CLIENT.worked(now).forEach((id, melted) -> submitSag(event, level.getEntity(id), melted));
        for (MorphingRemains.Morph morph : MorphingRemains.CLIENT.morphs(now)) {
            submitMorph(event, level, morph);
        }
    }

    /**
     * Submits the goo skin over a worked block that melts without the sag:
     * one holding contents, or one under a tap.
     *
     * @param event  the custom geometry submit event
     * @param level  the client level
     * @param pos    the block
     * @param melted the share melted
     */
    private static void submitSkin(SubmitCustomGeometryEvent event, ClientLevel level, BlockPos pos, float melted) {
        if (!level.getBlockState(pos).is(GooBlocks.MELTING_BLOCK.get()) && !level.getBlockState(pos).isAir()) {
            submitGoo(event, new AABB(pos).inflate(SKIN_GAP), SKIN, alphaOf(melted));
        }
    }

    /**
     * Submits the goo copy sagging in a melting mob's place.
     *
     * @param event  the custom geometry submit event
     * @param mob    the mob, or null once gone
     * @param melted the share melted
     */
    private static void submitSag(SubmitCustomGeometryEvent event, @Nullable Entity mob, float melted) {
        if (mob != null && !mob.isRemoved()) {
            AABB body = mob.getBoundingBox();
            submitGoo(event, body, sag(body, melted), GOO_ALPHA);
        }
    }

    /**
     * @param melted the share melted
     * @return a skin's alpha, thickening as the block melts
     */
    static int alphaOf(float melted) {
        return Math.round(GOO_ALPHA * melted);
    }

    /**
     * @param body   a mob's body
     * @param melted the share melted
     * @return the layers of its sagging goo copy
     */
    private static List<CuboidBounds> sag(AABB body, float melted) {
        return GooSag.layers((float) body.getXsize(), (float) body.getZsize(), (float) body.getYsize(), melted);
    }

    /**
     * Submits goo layers laid from a box's footprint corner.
     *
     * @param event  the custom geometry submit event
     * @param box    the box whose footprint corner the layers start at
     * @param layers the layers
     * @param alpha  the goo's alpha
     */
    private static void submitGoo(SubmitCustomGeometryEvent event, AABB box, List<CuboidBounds> layers, int alpha) {
        submitGooAt(event, new Vec3(box.minX, box.minY, box.minZ), layers, alpha);
    }

    /**
     * Submits goo layers laid from a world point.
     *
     * @param event  the custom geometry submit event
     * @param corner where the layers' origin stands, in world coordinates
     * @param layers the layers
     * @param alpha  the goo's alpha
     */
    private static void submitGooAt(SubmitCustomGeometryEvent event, Vec3 corner, List<CuboidBounds> layers,
                                    int alpha) {
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        Vec3 offset = corner.subtract(camera.pos);
        int color = ARGB.color(alpha, GooRenderUtil.OPAQUE_WHITE);
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(GooTypes.UNSTABLE));
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        GooSubmitter.submitFluid(poseStack, event.getSubmitNodeCollector(), ctx -> {
            for (CuboidBounds layer : layers) {
                ctx.emitBox(color, layer, uv);
            }
        });
        poseStack.popPose();
    }

    /**
     * Submits one morph: the remains, a rounded blob of goo, shrink from
     * their size to the goo item's and fade as the goo item grows in where
     * the item entity will stand.
     *
     * @param event the custom geometry submit event
     * @param level the client level
     * @param morph the morph
     */
    private static void submitMorph(SubmitCustomGeometryEvent event, ClientLevel level, MorphingRemains.Morph morph) {
        float progress = morph.progress();
        float size = morph.size() + (ITEM_SIZE - morph.size()) * progress;
        int blobAlpha = Math.round(GOO_ALPHA * (1f - smoothstep(ITEM_SHOWS, 1f, progress)));
        Vec3 corner = morph.at().subtract(size * HALF, size * HALF, size * HALF);
        submitGooAt(event, corner, GooSag.layers(size, size, size, 1f), blobAlpha);
        float itemScale = smoothstep(ITEM_SHOWS, 1f, progress);
        if (itemScale > 0f) {
            submitMorphItem(event, level, morph, itemScale);
        }
    }

    /**
     * Submits the goo item growing in where the item entity will stand, posed
     * as the item entity poses its model at rest.
     *
     * @param event     the custom geometry submit event
     * @param level     the client level
     * @param morph     the morph
     * @param itemScale how far the item has grown in, 0 to 1
     */
    private static void submitMorphItem(SubmitCustomGeometryEvent event, ClientLevel level,
                                        MorphingRemains.Morph morph, float itemScale) {
        Minecraft mc = Minecraft.getInstance();
        ItemStackRenderState item = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(item, GooStacks.createForOutput(morph.type(), SHOWN_GOO),
                ItemDisplayContext.GROUND, level, null, 0);
        if (item.isEmpty()) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        Vec3 at = morph.at().subtract(camera.pos);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(at.x, at.y + ITEM_LIFT - item.getModelBoundingBox().minY, at.z);
        poseStack.scale(itemScale, itemScale, itemScale);
        item.submit(poseStack, event.getSubmitNodeCollector(),
                LevelRenderer.getLightCoords(level, BlockPos.containing(morph.at())), OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * @param edge0 where the ramp starts
     * @param edge1 where it ends
     * @param x     the input
     * @return the smooth ramp from 0 at edge0 to 1 at edge1
     */
    static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (SMOOTH_BASE - SMOOTH_SLOPE * t);
    }
}

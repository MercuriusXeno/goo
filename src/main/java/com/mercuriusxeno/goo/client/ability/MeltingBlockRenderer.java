package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws what Unmake leaves on screen outside the melting block's own
 * renderer: the goo spreading over a block that melts without the sag, a
 * block holding contents or one under a tap; and the remains of anything
 * unmade and of a released soup, a blob of the goo they hold, drawn as the
 * crucible draws its goo, shrinking and rounding into the goo item where the
 * item then drops.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingBlockRenderer {

    /** The remains' alpha, nearly opaque goo. */
    static final int GOO_ALPHA = 0xEE;
    /** How big the remains end, in blocks, the size of the goo item on the ground. */
    static final float ITEM_SIZE = 0.25f;
    /** Where in the morph the goo item starts to show through the remains. */
    static final float ITEM_SHOWS = 0.4f;
    /** How far the item entity lifts its model off its feet at rest. */
    private static final float ITEM_LIFT = 0.1f;
    /** The goo an item stack shows the morph holds, the amount only picking its model. */
    private static final int SHOWN_GOO = 1000;
    private static final float HALF = 0.5f;
    private static final float WHOLE = 1f;
    /** The cubic smoothstep's constant term. */
    private static final float SMOOTH_BASE = 3f;
    /** The cubic smoothstep's slope term. */
    private static final float SMOOTH_SLOPE = 2f;
    private MeltingBlockRenderer() {
    }

    /**
     * Submits every spreading goo and morphing remains this frame.
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
        MeltingBlocks.CLIENT.worked(now).forEach((pos, melted) -> submitSpread(event, level, pos, melted, now));
        for (MorphingRemains.Morph morph : MorphingRemains.CLIENT.morphs(now)) {
            submitMorph(event, level, morph);
        }
    }

    /**
     * Submits the goo spreading over a worked block that melts without the
     * sag: one holding contents, or one under a tap.
     *
     * @param event  the custom geometry submit event
     * @param level  the client level
     * @param pos    the block
     * @param melted the share melted
     * @param now    the game time including the partial tick
     */
    private static void submitSpread(SubmitCustomGeometryEvent event, ClientLevel level, BlockPos pos, float melted,
                                     float now) {
        BlockState state = level.getBlockState(pos);
        if (state.is(GooBlocks.MELTING_BLOCK.get()) || state.isAir()) {
            return;
        }
        MeltMesh.Melt melt = new MeltMesh.Melt(state, level, pos, MeltMeshGoo.of(state), melted, now, false);
        Vec3 corner = Vec3.atLowerCornerOf(pos).subtract(event.getLevelRenderState().cameraRenderState.pos);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(corner.x, corner.y, corner.z);
        GooSubmitter.submitBody(poseStack, event.getSubmitNodeCollector(), LevelRenderer.getLightCoords(level, pos),
                ctx -> MeltMesh.emit(ctx, melt));
        poseStack.popPose();
    }

    /**
     * Submits one morph: the remains, a blob of goo standing on the ground or
     * hanging where the soup hung, shrink and round toward the goo item's size and fade as the
     * goo item grows in where the item entity will stand.
     *
     * @param event the custom geometry submit event
     * @param level the client level
     * @param morph the morph
     */
    private static void submitMorph(SubmitCustomGeometryEvent event, ClientLevel level, MorphingRemains.Morph morph) {
        float progress = morph.progress();
        float width = morph.size() + (ITEM_SIZE - morph.size()) * progress;
        float height = width * (morph.squat() + (WHOLE - morph.squat()) * progress);
        int alpha = Math.round(GOO_ALPHA * (WHOLE - smoothstep(ITEM_SHOWS, WHOLE, progress)));
        Vec3 center = morph.at().add(0, height * HALF, 0).subtract(event.getLevelRenderState().cameraRenderState.pos);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.scale(width * HALF, height * HALF, width * HALF);
        GooBall.submit(poseStack, event.getSubmitNodeCollector(), morph.goo(), alpha);
        poseStack.popPose();
        float itemScale = smoothstep(ITEM_SHOWS, WHOLE, progress);
        if (itemScale > 0f && !morph.goo().isEmpty()) {
            ResourceKey<GooTypeDefinition> itemType = morph.goo().largestType();
            submitMorphItem(event, level, morph, itemType, itemScale);
        }
    }

    /**
     * Submits the goo item growing in where the item entity will stand, posed
     * as the item entity poses its model at rest.
     *
     * @param event     the custom geometry submit event
     * @param level     the client level
     * @param morph     the morph
     * @param itemType  the goo type whose item it grows into
     * @param itemScale how far the item has grown in, 0 to 1
     */
    private static void submitMorphItem(SubmitCustomGeometryEvent event, ClientLevel level,
                                        MorphingRemains.Morph morph, ResourceKey<GooTypeDefinition> itemType,
                                        float itemScale) {
        ItemStackRenderState item = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item,
                GooStacks.createForOutput(itemType, SHOWN_GOO), ItemDisplayContext.GROUND, level, null, 0);
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

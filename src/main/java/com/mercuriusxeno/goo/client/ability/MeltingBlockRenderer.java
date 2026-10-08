package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
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
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3f;
import java.util.List;

/**
 * Draws what Unmake leaves on screen outside the melting block's own
 * renderer: the goo spreading over a block that melts without the sag, a
 * block holding contents or one under a tap; and the remains of anything
 * unmade, a squat blob of the goo it melted into, its types mingled,
 * shrinking and rounding into the goo item where the item then drops.
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
    /** How squat the remains start: their height as a share of their width. */
    static final float SQUAT = 0.35f;
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
    /** The most goo types layered over the blob. */
    private static final int MAX_LAYERS = 3;
    /** How fine the blob's patches are: field cells across it. */
    private static final float PATCH_SCALE = 1.6f;
    /** How far past its share a lesser type's patches cover, so a small share still shows. */
    private static final float PATCH_COVERAGE = 1.6f;
    /** How soft a patch's edge is, in field share either side of it. */
    private static final float PATCH_SOFTNESS = 0.08f;
    /** How far each further layer stands off the one under it, as a share of the blob's size. */
    private static final float LAYER_LIFT = 0.01f;
    private static final long LAYER_SALT = 0x9E37_79B9L;

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
     * Submits one morph: the remains, a squat blob of mingled goo sitting on
     * the ground, shrink and round toward the goo item's size and fade as the
     * goo item grows in where the item entity will stand.
     *
     * @param event the custom geometry submit event
     * @param level the client level
     * @param morph the morph
     */
    private static void submitMorph(SubmitCustomGeometryEvent event, ClientLevel level, MorphingRemains.Morph morph) {
        float progress = morph.progress();
        float width = morph.size() + (ITEM_SIZE - morph.size()) * progress;
        float height = width * (SQUAT + (WHOLE - SQUAT) * progress);
        int alpha = Math.round(GOO_ALPHA * (WHOLE - smoothstep(ITEM_SHOWS, WHOLE, progress)));
        int light = LevelRenderer.getLightCoords(level, BlockPos.containing(morph.at()));
        Vec3 center = morph.at().add(0, height * HALF, 0).subtract(event.getLevelRenderState().cameraRenderState.pos);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        poseStack.scale(width * HALF, height * HALF, width * HALF);
        GooSubmitter.submitBody(poseStack, event.getSubmitNodeCollector(), light,
                ctx -> emitBlob(ctx, morph.goo(), alpha));
        poseStack.popPose();
        float itemScale = smoothstep(ITEM_SHOWS, WHOLE, progress);
        ResourceKey<GooTypeDefinition> itemType = morph.goo().largest();
        if (itemScale > 0f && itemType != null) {
            submitMorphItem(event, level, morph, itemType, itemScale);
        }
    }

    /**
     * Emits a unit blob of mingled goo: a sphere of its largest type, the
     * others laid over it in soft patches by their shares, each type's sprite
     * wrapped about it mirrored across its back so the wrap leaves no seam.
     *
     * @param ctx   the render context, its pose scaled to the blob
     * @param goo   the goo
     * @param alpha the blob's alpha
     */
    private static void emitBlob(RenderContext ctx, MingledGoo goo, int alpha) {
        int layers = Math.min(goo.types().size(), MAX_LAYERS);
        for (int layer = 0; layer < layers; layer++) {
            emitBlobLayer(ctx, goo.types().get(layer), layer, goo.share(layer), alpha);
        }
    }

    /**
     * Emits one goo type's layer of the blob, each vertex as opaque as the
     * layer is there: the base everywhere, a lesser type only in its patches.
     *
     * @param ctx   the render context, its pose scaled to the blob
     * @param type  the goo type
     * @param index its index, largest first
     * @param share its share of the whole
     * @param alpha the blob's alpha
     */
    private static void emitBlobLayer(RenderContext ctx, ResourceKey<GooTypeDefinition> type, int index, float share,
                                      int alpha) {
        List<Vector3f> mesh = NetherSphereVisual.unitSphereMesh();
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(type));
        float scale = WHOLE + LAYER_LIFT * index;
        float edge = WHOLE - share * PATCH_COVERAGE;
        for (Vector3f point : mesh) {
            float cover = index == 0 ? WHOLE : smoothstep(edge - PATCH_SOFTNESS, edge + PATCH_SOFTNESS,
                    (float) MeltMeshNoise.smooth(point.x() * PATCH_SCALE, point.y() * PATCH_SCALE,
                            point.z() * PATCH_SCALE, index * LAYER_SALT));
            float u = (float) (Math.abs(Math.atan2(point.z(), point.x())) / Math.PI);
            float v = point.y() * HALF + HALF;
            ctx.vertexColored(ARGB.color(Math.round(alpha * cover), GooRenderUtil.OPAQUE_WHITE),
                    point.x() * scale, point.y() * scale, point.z() * scale,
                    sprite.u0() + (sprite.u1() - sprite.u0()) * u, sprite.v0() + (sprite.v1() - sprite.v0()) * v,
                    point.x(), point.y(), point.z());
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

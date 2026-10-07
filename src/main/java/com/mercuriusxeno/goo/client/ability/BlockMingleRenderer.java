package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws each playing block transform: the old block's model, a hair larger
 * than the block so it covers the new one on every face, through the mingle
 * pipeline, which lets the new block show through more of it as the
 * transform runs (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BlockMingleRenderer {

    /** Units the overlay coordinates carry the progress in; must match PROGRESS_UNITS in block_mingle.vsh. */
    static final int PROGRESS_UNITS = 4096;
    /** How far the old block swells past the new so its faces sit just in front. */
    private static final float SWELL = 1.002f;
    private static final float BLOCK_CENTER = 0.5f;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final long MODEL_SEED = 42L;

    private BlockMingleRenderer() {
    }

    /**
     * Draws the playing transforms after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        List<BlockTransforms.Transform> live = BlockTransforms.CLIENT.live(level.getGameTime());
        if (live.isEmpty()) {
            return;
        }
        float gameTime = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType mingle = GooRenderTypes.blockMingle(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        VertexConsumer consumer = buffers.getBuffer(mingle);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        for (BlockTransforms.Transform transform : live) {
            drawTransform(event.getPoseStack(), consumer, camera, transform, gameTime);
        }
        buffers.endBatch(mingle);
    }

    /**
     * Draws one transform's old block, swelled about its center, its progress on every quad.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param consumer  the mingle buffer
     * @param camera    the camera's world position
     * @param transform the transform playing
     * @param gameTime  the game time including the partial tick
     */
    private static void drawTransform(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                      BlockTransforms.Transform transform, float gameTime) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        poseStack.pushPose();
        poseStack.translate(transform.pos().getX() - camera.x + BLOCK_CENTER,
                transform.pos().getY() - camera.y + BLOCK_CENTER, transform.pos().getZ() - camera.z + BLOCK_CENTER);
        poseStack.scale(SWELL, SWELL, SWELL);
        poseStack.translate(-BLOCK_CENTER, -BLOCK_CENTER, -BLOCK_CENTER);
        QuadInstance instance = new QuadInstance();
        instance.setColor(OPAQUE_WHITE);
        instance.setLightCoords(LevelRenderer.getLightCoords(level, transform.pos()));
        instance.setOverlayCoords(progressCoords(transform.progress(gameTime)));
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(transform.from());
        for (BakedQuad quad : quadsOf(model, level, transform)) {
            consumer.putBakedQuad(poseStack.last(), quad, instance);
        }
        poseStack.popPose();
    }

    /**
     * The overlay coordinates carrying a transform's progress.
     *
     * @param progress the share run, from 0 to 1
     * @return the packed overlay coordinates, progress in the low half
     */
    static int progressCoords(float progress) {
        return Math.round(Math.clamp(progress, 0f, 1f) * PROGRESS_UNITS);
    }

    /**
     * Every quad of the old block's model, on every face and the faceless ones.
     *
     * @param model     the old block's model
     * @param level     the client level
     * @param transform the transform playing
     * @return the quads
     */
    private static List<BakedQuad> quadsOf(BlockStateModel model, ClientLevel level,
                                           BlockTransforms.Transform transform) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, transform.pos(), transform.from(), RandomSource.create(MODEL_SEED), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                quads.addAll(part.getQuads(side));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }
}

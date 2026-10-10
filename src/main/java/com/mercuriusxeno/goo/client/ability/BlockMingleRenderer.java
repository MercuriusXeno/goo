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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
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
 * transform runs; and each block part way to its next calcify rung, the
 * next block's model mingled in over it by the share built
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BlockMingleRenderer {

    /** Units the overlay coordinates carry the progress in; must match PROGRESS_UNITS in block_mingle.vsh. */
    static final int PROGRESS_UNITS = 4096;
    /** How far the old block swells past the new so its faces sit just in front. */
    private static final float SWELL = 1.002f;
    private static final float BLOCK_CENTER = 0.5f;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF000000;
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
        List<BlockTransforms.Exposure> exposing = BlockTransforms.CLIENT.exposing(level.getGameTime());
        if (live.isEmpty() && exposing.isEmpty()) {
            return;
        }
        float gameTime = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawAll(event.getPoseStack(), live, exposing, gameTime);
    }

    /**
     * Draws the playing transforms and the partial exposures into one mingle batch.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param live      the transforms playing
     * @param exposing  the blocks part way to their next rung
     * @param gameTime  the game time including the partial tick
     */
    private static void drawAll(PoseStack poseStack, List<BlockTransforms.Transform> live,
                                List<BlockTransforms.Exposure> exposing, float gameTime) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType mingle = GooRenderTypes.blockMingle(mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        VertexConsumer consumer = buffers.getBuffer(mingle);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        for (BlockTransforms.Transform transform : live) {
            drawMingle(poseStack, consumer, camera,
                    new Mingle(transform.pos(), transform.from(), transform.progress(gameTime), OPAQUE_WHITE));
        }
        // petrify-stone-encasement-and-calcify-map: the next rung mingles in by the share built so far
        for (BlockTransforms.Exposure exposure : exposing) {
            drawMingle(poseStack, consumer, camera, new Mingle(exposure.pos(), exposure.toward(),
                    1f - exposure.share(), mingleColor(exposure.tint())));
        }
        buffers.endBatch(mingle);
    }

    /**
     * One block state drawn over a block through the mingle, with the share
     * of it already dissolved.
     *
     * @param pos      the block
     * @param state    the state drawn over it
     * @param progress the share of the drawn state dissolved, 0 whole to 1 gone
     * @param color    the ARGB the drawn state is tinted by
     */
    private record Mingle(BlockPos pos, BlockState state, float progress, int color) {
    }

    /**
     * The color an exposure's mingled block is drawn in: its own colors
     * untinted, or the tint the ability sent, opaque
     * (decision decay-gnats-degrade-each-block-once).
     *
     * @param tint the RGB the exposure carries, negative for none
     * @return the ARGB to draw it in
     */
    static int mingleColor(int tint) {
        return tint < 0 ? OPAQUE_WHITE : OPAQUE_ALPHA | tint;
    }

    /**
     * The light a drawn state over a block wears: the brightest light on any
     * of the block's faces, since a solid block holds no light inside it and
     * its own position reads black.
     *
     * @param level the client level
     * @param pos   the block
     * @return the packed light coordinates
     */
    private static int faceLight(ClientLevel level, BlockPos pos) {
        int brightest = LevelRenderer.getLightCoords(level, pos);
        for (Direction side : Direction.values()) {
            brightest = LightCoordsUtil.max(brightest, LevelRenderer.getLightCoords(level, pos.relative(side)));
        }
        return brightest;
    }

    /**
     * Draws a state over a block, swelled about its center, its progress on every quad.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param consumer  the mingle buffer
     * @param camera    the camera's world position
     * @param mingle    the state, the block and the progress
     */
    private static void drawMingle(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Mingle mingle) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        poseStack.pushPose();
        poseStack.translate(mingle.pos().getX() - camera.x + BLOCK_CENTER,
                mingle.pos().getY() - camera.y + BLOCK_CENTER, mingle.pos().getZ() - camera.z + BLOCK_CENTER);
        poseStack.scale(SWELL, SWELL, SWELL);
        poseStack.translate(-BLOCK_CENTER, -BLOCK_CENTER, -BLOCK_CENTER);
        QuadInstance instance = new QuadInstance();
        instance.setColor(mingle.color());
        instance.setLightCoords(faceLight(level, mingle.pos()));
        instance.setOverlayCoords(progressCoords(mingle.progress()));
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(mingle.state());
        for (BakedQuad quad : quadsOf(model, level, mingle)) {
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
     * Every quad of a drawn state's model, on every face and the faceless ones.
     *
     * @param model  the drawn state's model
     * @param level  the client level
     * @param mingle the state and the block it is drawn over
     * @return the quads
     */
    private static List<BakedQuad> quadsOf(BlockStateModel model, ClientLevel level, Mingle mingle) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, mingle.pos(), mingle.state(), RandomSource.create(MODEL_SEED), parts);
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

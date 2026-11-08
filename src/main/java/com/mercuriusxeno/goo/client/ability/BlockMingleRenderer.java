package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws each playing block transform: the old block's model, on the block's
 * own faces and pulled in front of the new one by depth bias, through the mingle
 * pipeline, which lets the new block show through more of it as the
 * transform runs; and each block part way to its next calcify rung, the
 * next block's model mingled in over it by the share built
 * (decision petrify-stone-encasement-and-calcify-map).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BlockMingleRenderer {

    /** Units the overlay coordinates carry the progress in; must match PROGRESS_UNITS in block_mingle.vsh. */
    static final int PROGRESS_UNITS = 4096;
    /** The packed overlay's V half sits above its 16-bit U half. */
    private static final int OVERLAY_V_SHIFT = 16;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int OPAQUE_ALPHA = 0xFF000000;

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
        drawAll(event.getPoseStack(), level, live, exposing, gameTime);
    }

    /**
     * Draws the playing transforms and the partial exposures into one mingle batch.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param level     the client level the blocks stand in
     * @param live      the transforms playing
     * @param exposing  the blocks part way to their next rung
     * @param gameTime  the game time including the partial tick
     */
    private static void drawAll(PoseStack poseStack, ClientLevel level, List<BlockTransforms.Transform> live,
                                List<BlockTransforms.Exposure> exposing, float gameTime) {
        Minecraft mc = Minecraft.getInstance();
        List<Mingle> mingles = new ArrayList<>();
        for (BlockTransforms.Transform transform : live) {
            mingles.add(new Mingle(transform.pos(), transform.from(), transform.progress(gameTime),
                    mingleColor(transform.tint()), transform.seed()));
        }
        // petrify-stone-encasement-and-calcify-map: the next rung mingles in by the share built so far
        for (BlockTransforms.Exposure exposure : exposing) {
            BlockTransforms.minglingOf(exposure, level.getBlockState(exposure.pos())).ifPresent(mingling ->
                    mingles.add(new Mingle(exposure.pos(), mingling.state(), 1f - exposure.share(),
                            mingleColor(mingling.tint()), BlockTransforms.CLIENT.seedOf(exposure))));
        }
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        drawPass(poseStack, mingles, GooRenderTypes.blockMingle(atlas), false);
        // coat-is-a-green-slime-film-over-the-blocks-own-texture: a translucent block mingles in see-through
        drawPass(poseStack, mingles, GooRenderTypes.blockMingleTranslucent(atlas), true);
    }

    /**
     * Draws one batch of the mingles: the opaque quads, or the translucent ones.
     *
     * @param poseStack   the level's pose stack, camera relative
     * @param mingles     the states mingling over their blocks
     * @param renderType  the batch's render type
     * @param translucent true for the translucent quads, false for the rest
     */
    private static void drawPass(PoseStack poseStack, List<Mingle> mingles, RenderType renderType,
                                 boolean translucent) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        ModelBlockRenderer tesselator = worldTesselator(mc);
        for (Mingle mingle : mingles) {
            drawMingle(poseStack, consumer, camera, tesselator, mingle, translucent);
        }
        buffers.endBatch(renderType);
    }

    /**
     * One block state drawn over a block through the mingle, with the share
     * of it already dissolved.
     *
     * @param pos      the block
     * @param state    the state drawn over it
     * @param progress the share of the drawn state dissolved, 0 whole to 1 gone
     * @param color    the ARGB the drawn state is tinted by
     * @param seed     the transition's mingle seed, so each use dissolves through its own field
     */
    private record Mingle(BlockPos pos, BlockState state, float progress, int color, int seed) {
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
     * Draws a state over a block on the block's own faces, tesselated as the
     * world tesselates it, so its face shade, ambient occlusion, light and biome
     * tint match the block the world draws there once the transform ends; each
     * quad then carries the transform's progress and seed and wears its tint.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param consumer  the mingle buffer
     * @param camera    the camera's world position
     * @param tesselator the world's block tesselation
     * @param mingle    the state, the block and the progress
     * @param translucent true to draw the state's translucent quads alone, false for the rest
     */
    private static void drawMingle(PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                   ModelBlockRenderer tesselator, Mingle mingle, boolean translucent) {
        Minecraft mc = Minecraft.getInstance();
        poseStack.pushPose();
        // The faces sit on the block's own, so nothing shifts when the world's block takes over;
        // the pipeline's depth bias lets them win there.
        poseStack.translate(mingle.pos().getX() - camera.x, mingle.pos().getY() - camera.y,
                mingle.pos().getZ() - camera.z);
        PoseStack.Pose pose = poseStack.last();
        int overlay = overlayCoords(mingle.progress(), mingle.seed());
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(mingle.state());
        tesselator.tesselateBlock((x, y, z, quad, instance) -> {
            if (drawsTranslucent(quad) == translucent) {
                dressQuad(instance, mingle.color(), overlay);
                consumer.putBakedQuad(pose, quad, instance);
            }
        }, 0f, 0f, 0f, mc.level, mingle.pos(), mingle.state(), model, modelSeed(mingle.state(), mingle.pos()));
        poseStack.popPose();
    }

    /**
     * The world's block tesselation, under the player's ambient occlusion setting.
     *
     * @param mc the client
     * @return a tesselator lighting blocks as the chunk builder does
     */
    private static ModelBlockRenderer worldTesselator(Minecraft mc) {
        return new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true, mc.getBlockColors());
    }

    /**
     * The seed a drawn state's model picks its variant by.
     *
     * @param state the drawn state
     * @param pos   the block it is drawn over
     * @return the seed
     */
    static long modelSeed(BlockState state, BlockPos pos) {
        // The world tesselates a block with this seed, so the drawn block wears the variant the world swaps in after.
        return state.getSeed(pos);
    }

    /**
     * Dresses one world-lit quad for the mingle: its baked colors, the world's
     * shade, occlusion and biome tint, take the transform's tint over them, and
     * its overlay carries the progress and seed.
     *
     * @param instance    the quad's lighting as the world tesselated it
     * @param mingleColor the ARGB the drawn state is tinted by, opaque white for none
     * @param overlay     the overlay coordinates carrying the progress and seed
     */
    static void dressQuad(QuadInstance instance, int mingleColor, int overlay) {
        if (mingleColor != OPAQUE_WHITE) {
            instance.multiplyColor(mingleColor);
        }
        instance.setOverlayCoords(overlay);
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
     * Whether a quad of a mingled block draws through the blended mingle, as
     * a translucent block's quads do, so a slime coat building in stays
     * translucent rather than opaque until it lands
     * (decision coat-is-a-green-slime-film-over-the-blocks-own-texture).
     *
     * @param quad the quad
     * @return true for a quad on the translucent layer
     */
    static boolean drawsTranslucent(BakedQuad quad) {
        return quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
    }

    /**
     * The overlay coordinates carrying a transition's progress in U and its
     * mingle seed in V (decision diagnose-then-fix-dissolve-repeat).
     *
     * @param progress the share run, from 0 to 1
     * @param seed     the mingle seed, from 0 to {@link BlockTransforms#SEED_UNITS} less one
     * @return the packed overlay coordinates
     */
    static int overlayCoords(float progress, int seed) {
        return progressCoords(progress) | Math.floorMod(seed, BlockTransforms.SEED_UNITS) << OVERLAY_V_SHIFT;
    }
}

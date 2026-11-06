package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a block hunk as its blocks, each at its offset from the hunk's origin
 * and lit by the world where it stands now, as a falling block draws.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
public final class BlockHunkRenderer extends EntityRenderer<BlockHunk, BlockHunkRenderer.HunkRenderState> {

    private static final double HALF = 0.5;
    /** A hair under a block's top face, inside the cell its top stands in. */
    private static final double JUST_UNDER_THE_TOP = 0.99;

    /** The hunk's blocks as this frame draws them, each with its offset from the hunk's position. */
    public static final class HunkRenderState extends EntityRenderState {
        final List<MovingBlockRenderState> blocks = new ArrayList<>();
        final List<Vec3> offsets = new ArrayList<>();
    }

    /**
     * @param context the renderer context
     */
    public BlockHunkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public HunkRenderState createRenderState() {
        return new HunkRenderState();
    }

    @Override
    public void extractRenderState(BlockHunk hunk, HunkRenderState state, float partialTick) {
        super.extractRenderState(hunk, state, partialTick);
        state.blocks.clear();
        state.offsets.clear();
        if (!(hunk.level() instanceof ClientLevel level)) {
            return;
        }
        Vec3 position = hunk.getPosition(partialTick);
        for (BlockHunk.HunkBlock block : hunk.blocks()) {
            if (block.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            Vec3 corner = BlockHunk.cornerOf(position, block);
            // Lit from the cell its top stands in, so a block breaking out of the ground draws in daylight.
            BlockPos lightAt = BlockPos.containing(corner.add(HALF, JUST_UNDER_THE_TOP, HALF));
            MovingBlockRenderState moving = new MovingBlockRenderState();
            moving.randomSeedPos = lightAt;
            moving.blockPos = lightAt;
            moving.blockState = block.state();
            moving.biome = level.getBiome(lightAt);
            moving.cardinalLighting = level.cardinalLighting();
            moving.lightEngine = level.getLightEngine();
            state.blocks.add(moving);
            state.offsets.add(corner.subtract(position));
        }
    }

    @Override
    public void submit(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        for (int index = 0; index < state.blocks.size(); index++) {
            Vec3 offset = state.offsets.get(index);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            collector.submitMovingBlock(poseStack, state.blocks.get(index));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}

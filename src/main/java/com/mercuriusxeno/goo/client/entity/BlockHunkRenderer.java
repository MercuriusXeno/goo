package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a block hunk as its blocks, each at its offset from the hunk's origin
 * and lit by the world where it stands now, as a falling block draws.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 *
 * A settled hunk lingering over its stone draws its blocks a hair swollen,
 * so they cover the chunk's own stone without fighting it for depth.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 *
 * A hunk the local player holds, once the server has brought it to the hold,
 * draws where the player's own look puts it this frame, by the same hold the
 * server keeps it at and only as far as the world lets it move, so it glides
 * with the look and never draws inside a block; while it still rises out of
 * its hole or travels in, it draws where the server moves it. A struck hunk
 * draws nothing: HunkCrumbles draws it coming apart.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public final class BlockHunkRenderer extends EntityRenderer<BlockHunk, BlockHunkRenderer.HunkRenderState> {

    private static final double HALF = 0.5;
    /** A hair under a block's top face, inside the cell its top stands in. */
    private static final double JUST_UNDER_THE_TOP = 0.99;
    /** How far a lingering hunk's blocks swell past the stone beneath them. */
    private static final float LINGER_SWELL = 1.002f;
    private static final float WHOLE = 1f;
    /** How near the hold the server must have brought a hunk before the holder's look draws it. */
    private static final double AT_THE_HOLD = 1.0;

    /** The hunk's blocks as this frame draws them, each with its offset from the hunk's drawn position. */
    public static final class HunkRenderState extends EntityRenderState {
        final List<MovingBlockRenderState> blocks = new ArrayList<>();
        final List<Vec3> offsets = new ArrayList<>();
        float swell = WHOLE;
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
        if (!(hunk.level() instanceof ClientLevel level) || hunk.shattering()) {
            return;
        }
        Vec3 lerped = hunk.getPosition(partialTick);
        Vec3 drawnAt = heldOnTheLocalLook(hunk, partialTick);
        Vec3 position = drawnAt == null ? lerped : drawnAt;
        state.swell = hunk.lingers(level.getGameTime()) ? LINGER_SWELL : WHOLE;
        hunk.blocks().stream().filter(block -> block.state().getRenderShape() == RenderShape.MODEL).forEach(block -> {
            Vec3 corner = BlockHunk.cornerOf(position, block);
            state.blocks.add(movingBlock(level, block, corner, BlockHunk.variantCellOf(hunk.home(), block)));
            // The pose stands at the lerped position, so a block drawn elsewhere is offset from it.
            state.offsets.add(corner.subtract(lerped));
        });
    }

    private static MovingBlockRenderState movingBlock(ClientLevel level, BlockHunk.HunkBlock block, Vec3 corner,
                                                      BlockPos variantCell) {
        // Lit from the cell its top stands in, so a block breaking out of the ground draws in daylight.
        BlockPos lightAt = BlockPos.containing(corner.add(HALF, JUST_UNDER_THE_TOP, HALF));
        MovingBlockRenderState moving = new MovingBlockRenderState();
        // Seeded by its home cell, so its texture's variant holds still as it moves.
        moving.randomSeedPos = variantCell;
        moving.blockPos = lightAt;
        moving.blockState = block.state();
        moving.biome = level.getBiome(lightAt);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        return moving;
    }

    /**
     * Where a hunk the local player holds draws this frame: on the player's
     * own look, by the selected Terrakinesis's hold.
     *
     * @param hunk        the hunk
     * @param partialTick the frame's partial tick
     * @return the position, or null for a hunk the local player does not hold
     */
    private static @Nullable Vec3 heldOnTheLocalLook(BlockHunk hunk, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        TerrakinesisStep terrakinesis = player == null || hunk.holderId() != player.getId() ? null
                : selectedTerrakinesis(player);
        if (terrakinesis == null) {
            return null;
        }
        Vec3 lerped = hunk.getPosition(partialTick);
        Vec3 onTheLook = terrakinesis.holdPosition(player.getEyePosition(partialTick),
                player.getViewVector(partialTick), player.getViewXRot(partialTick),
                hunk.getBoundingBox().getCenter().subtract(hunk.position()));
        if (onTheLook.distanceTo(lerped) > AT_THE_HOLD) {
            return null;
        }
        AABB drawnBox = hunk.getBoundingBox().move(lerped.subtract(hunk.position()));
        return lerped.add(Entity.collideBoundingBox(hunk, onTheLook.subtract(lerped), drawnBox, hunk.level(),
                List.of()));
    }

    private static @Nullable TerrakinesisStep selectedTerrakinesis(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? null : ability.behaviors().stream().filter(TerrakinesisStep.class::isInstance)
                .map(TerrakinesisStep.class::cast).findFirst().orElse(null);
    }

    @Override
    public void submit(HunkRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        for (int index = 0; index < state.blocks.size(); index++) {
            Vec3 offset = state.offsets.get(index);
            poseStack.pushPose();
            poseStack.translate(offset.x + HALF, offset.y + HALF, offset.z + HALF);
            poseStack.scale(state.swell, state.swell, state.swell);
            poseStack.translate(-HALF, -HALF, -HALF);
            collector.submitMovingBlock(poseStack, state.blocks.get(index));
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}

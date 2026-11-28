package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.alientech.FaceMountedBlock;
import com.mercuriusxeno.goo.block.alientech.LightBeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the light beacon's far-seen column out the side opposite the face it attaches by; the beacon itself is the
 * block model. The column draws from as far as the render distance reaches.
 * decision bulb-one-model-max-light-beacon-combo
 */
public class LightBeaconRenderer implements BlockEntityRenderer<LightBeaconBlockEntity, LightBeaconRenderState> {

    /** The share of the render distance a beacon draws to: two thirds. */
    private static final int VIEW_SHARE_NUMERATOR = 2;
    private static final int VIEW_SHARE_DENOMINATOR = 3;

    /**
     * Creates the light beacon renderer.
     *
     * @param context the renderer context
     */
    public LightBeaconRenderer(BlockEntityRendererProvider.Context context) {
        // The column is procedural; the context carries nothing it draws from.
    }

    @Override
    public LightBeaconRenderState createRenderState() {
        return new LightBeaconRenderState();
    }

    @Override
    public void extractRenderState(LightBeaconBlockEntity beacon, LightBeaconRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(beacon, state, breakProgress);
        state.facing = beacon.getBlockState().getValue(FaceMountedBlock.FACING);
        state.animationTime = (beacon.getLevel() == null ? 0L : beacon.getLevel().getGameTime()) + partialTick;
        state.radiusScale = BeaconBeams.radiusScale(
                (float) cameraPos.subtract(Vec3.atCenterOf(state.blockPos)).horizontalDistance());
        state.columnLength = beacon.getLevel() == null ? BeaconBeams.BEAM_REACH
                : BeaconBeams.columnLength(beacon.getLevel(), state.blockPos, state.facing);
        state.fadeOut = getViewDistance();
    }

    @Override
    public void submit(LightBeaconRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        BeaconBeams.queue(poseStack, Vec3.atCenterOf(state.blockPos), state.facing, state.animationTime,
                state.radiusScale, state.columnLength, state.fadeOut);
    }

    @Override
    public AABB getRenderBoundingBox(LightBeaconBlockEntity beacon) {
        return BeaconBeams.drawnBounds(beacon.getBlockPos(), beacon.getBlockState().getValue(FaceMountedBlock.FACING),
                BeaconBeams.BEAM_REACH);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /**
     * Two thirds of the render distance, so the column fades out and its beacon stops drawing a little short of
     * where its chunk would unload (operator ruling 2026-11-28).
     */
    @Override
    public int getViewDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * SectionPos.SECTION_SIZE
                * VIEW_SHARE_NUMERATOR / VIEW_SHARE_DENOMINATOR;
    }
}

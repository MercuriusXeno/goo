package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ability.CrystalCloudVisual;
import com.mercuriusxeno.goo.client.ability.MarkerOrbVisual;
import com.mercuriusxeno.goo.client.ability.MetalSpikeVisual;
import com.mercuriusxeno.goo.client.ability.ThumpRings;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Thin dispatcher for ability block visuals. Each ability-specific
 * appearance lives in its own visualizer in {@code client.ability/}; this
 * class extracts the common state fields, delegates ability-specific
 * extraction to each visualizer, and dispatches submit() based on which
 * behavior is active.
 *
 * <p>Active visualizers:
 * <ul>
 *   <li>{@link MarkerOrbVisual} - the slime-like orb while the program runs</li>
 *   <li>{@link MetalSpikeVisual} - cone spikes from the marker to tracked entities</li>
 *   <li>{@link CrystalCloudVisual} - the shard cloud a crystal marker stands</li>
 *   <li>{@link NetherHoleStyles#active()} - the swappable nether black-hole style</li>
 * </ul>
 */
public class AbilityBlockRenderer
        implements BlockEntityRenderer<AbilityBlockEntity, AbilityBlockRenderState> {

    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Half-extent of the render bounding box around a ability block, in blocks.
     * Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;

    public AbilityBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Copies goo type and partial tick from the block entity.
     *
     * @param be          the block entity
     * @param state       the render state to populate
     * @param partialTick the partial tick for interpolation
     */
    private static void extractCoreFields(AbilityBlockEntity be,
                                          AbilityBlockRenderState state, float partialTick) {
        state.gooType = be.getGooType();
        state.partialTick = partialTick;
        state.gameTime = be.getLevel() != null
                ? be.getLevel().getGameTime() + partialTick : 0f;
    }

    /**
     * Detects aim targeting and copies the placed face and behavior state.
     *
     * @param be    the block entity
     * @param state the render state to populate
     */
    private static void extractTargetAndFace(AbilityBlockEntity be,
                                             AbilityBlockRenderState state) {
        // Highlight when the vanilla crosshair or the post-throw freeze
        // window (aim locked from the previous throw) is on this block.
        BlockPos pos = be.getBlockPos();
        state.targeted = GooRenderUtil.isBlockTargeted(pos) || ThrowFreezeState.isFrozenOnBlock(pos);
        state.placedFace = be.getPlacedFace();
        state.behaviorActive = be.getBehavior() != null;
    }

    @Override
    public AbilityBlockRenderState createRenderState() {
        return new AbilityBlockRenderState();
    }

    /**
     * Extends the render bounding box so the implosion sphere (up to the
     * nether max radius of 9) is not frustum-culled when the player looks
     * slightly away from the marker block.
     *
     * @param blockEntity the ability block block entity
     * @return an AABB large enough to contain the maximum implosion sphere
     */
    @Override
    public @NonNull AABB getRenderBoundingBox(@NonNull AbilityBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        double cx = pos.getX() + BLOCK_CENTER;
        double cy = pos.getY() + BLOCK_CENTER;
        double cz = pos.getZ() + BLOCK_CENTER;
        return new AABB(
                cx - RENDER_BOX_HALF_EXTENT, cy - RENDER_BOX_HALF_EXTENT, cz - RENDER_BOX_HALF_EXTENT,
                cx + RENDER_BOX_HALF_EXTENT, cy + RENDER_BOX_HALF_EXTENT, cz + RENDER_BOX_HALF_EXTENT);
    }

    @Override
    public void extractRenderState(AbilityBlockEntity be,
                                   AbilityBlockRenderState state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        extractCoreFields(be, state, partialTick);
        extractTargetAndFace(be, state);
        state.abilityId = be.getAbilityId();
        MetalSpikeVisual.extract(be, state);
        CrystalCloudVisual.extract(be, state);
        NetherHoleStyles.active().extract(be, state);
        // thumper-blob-pulses-periodically-then-fades
        boolean powered = be.getBlockState().getOptionalValue(AbilityBlock.POWERED).orElse(false);
        ThumpRings.see(be.getBlockPos(), be.getPlacedFace(), powered);
    }

    @Override
    public void submit(AbilityBlockRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.netherActive) {
            NetherHoleStyles.active().submit(state, poseStack, nodeCollector);
            return;
        }
        MarkerOrbVisual.submit(state, poseStack, nodeCollector);
        if (state.crystalActive) {
            CrystalCloudVisual.submit(state, poseStack, nodeCollector);
        }
        if (!state.spikeAnims.isEmpty()) {
            MetalSpikeVisual.submit(state, poseStack, nodeCollector);
        }
    }
}

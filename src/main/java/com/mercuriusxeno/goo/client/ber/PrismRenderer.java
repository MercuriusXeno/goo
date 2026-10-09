package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.ability.program.AgitationState;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.client.ability.TransformationRenderer;
import com.mercuriusxeno.goo.client.ber.style.AgitatorPrismStyle;
import com.mercuriusxeno.goo.client.ber.style.PrismComboStyle;
import com.mercuriusxeno.goo.client.ber.style.PrismComboStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the prism's milky quartz crystal from its baked model, scaled about
 * the center of the face it grew from by the transformation that grows it
 * out of the landing blob. A prism holding a combo draws by the style its
 * combo registered in {@link PrismComboStyles}.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 * decision prism-hosts-the-combos
 */
public class PrismRenderer implements BlockEntityRenderer<PrismBlockEntity, PrismRenderState> {

    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    private static final float HALF = 0.5f;
    /** Packed outline color for none. */
    private static final int NO_OUTLINE = 0;

    private final BlockModelResolver blockModels;

    /**
     * Creates the prism renderer.
     *
     * @param context the renderer context
     */
    public PrismRenderer(BlockEntityRendererProvider.Context context) {
        this.blockModels = context.blockModelResolver();
    }

    @Override
    public PrismRenderState createRenderState() {
        return new PrismRenderState();
    }

    @Override
    public void extractRenderState(PrismBlockEntity prism, PrismRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(prism, state, breakProgress);
        blockModels.update(state.crystal, prism.getBlockState(), DISPLAY_CONTEXT);
        state.facing = prism.getBlockState().getValue(PrismBlock.FACING);
        state.scale = TransformationRenderer.blockModelScale(prism.getBlockPos());
        state.combo = prism.getCombo();
        // agitator-prism-quickens-until-a-spawn: the beat rides the synced countdown
        AgitationState agitation = prism.programState().agitation();
        state.beat = agitation.interval() > 0
                ? AgitatorPrismStyle.beat(agitation.interval() - agitation.countdown() + partialTick)
                : 0f;
    }

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        if (state.scale <= 0f) {
            return;
        }
        poseStack.pushPose();
        scaleAboutBase(poseStack, state.facing, state.scale);
        // prism-hosts-the-combos: a combined prism draws by its combo's style, a plain one as the crystal
        PrismComboStyle style = PrismComboStyles.forCombo(state.combo);
        if (style == null) {
            state.crystal.submit(poseStack, nodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, NO_OUTLINE);
        } else {
            style.submit(state, poseStack, nodeCollector);
        }
        poseStack.popPose();
    }

    /**
     * Scales the pose about the center of the face the prism grew from, so the
     * crystal grows out of the face rather than out of the middle of the cell.
     *
     * @param poseStack the pose stack at the cell's corner
     * @param facing    the face the prism grew from
     * @param scale     the prism's size
     */
    private static void scaleAboutBase(PoseStack poseStack, Direction facing, float scale) {
        float baseX = HALF - HALF * facing.getStepX();
        float baseY = HALF - HALF * facing.getStepY();
        float baseZ = HALF - HALF * facing.getStepZ();
        poseStack.translate(baseX, baseY, baseZ);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-baseX, -baseY, -baseZ);
    }
}

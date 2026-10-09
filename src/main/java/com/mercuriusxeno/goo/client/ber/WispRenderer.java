package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.block.ability.WispBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.ColorSphere;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a wisp as a soft floating mote: a small near-white core inside a
 * glow-yellow halo, both added onto the world, bobbing slowly in place and
 * shrinking and dimming through the wisp's fade stages.
 * decision radiant-wisps-where-light-is-low
 */
public class WispRenderer implements BlockEntityRenderer<WispBlockEntity, WispRenderer.WispRenderState> {

    static final float CORE_RADIUS = 0.07f;
    static final float HALO_RADIUS = 0.18f;
    private static final int CORE_ALPHA = 210;
    private static final int HALO_ALPHA = 55;
    private static final int CORE_RGB = 0xFFF6C8;
    private static final int HALO_RGB = 0xFFE628;
    private static final float BOB_HEIGHT = 0.06f;
    private static final float BOB_PER_TICK = 0.08f;
    private static final double HALF = 0.5;
    /** Spreads the bob's phase by position, so neighboring wisps never bob in step. */
    private static final int PHASE_SPREAD = 31;

    /**
     * Creates the wisp renderer.
     *
     * @param context the renderer context
     */
    public WispRenderer(BlockEntityRendererProvider.Context context) {
        // The mote is procedural; the context carries nothing it draws from.
    }

    @Override
    public WispRenderState createRenderState() {
        return new WispRenderState();
    }

    @Override
    public void extractRenderState(WispBlockEntity wisp, WispRenderState state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(wisp, state, breakProgress);
        state.fade = wisp.getBlockState().getValue(WispBlock.FADE);
        long gameTime = wisp.getLevel() == null ? 0L : wisp.getLevel().getGameTime();
        state.time = gameTime + partialTick + wisp.getBlockPos().hashCode() % PHASE_SPREAD;
    }

    @Override
    public void submit(WispRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        float strength = strength(state.fade);
        Vec3 center = new Vec3(HALF, HALF + BOB_HEIGHT * Mth.sin(state.time * BOB_PER_TICK), HALF);
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.GLOW_SHELL_TYPE, (pose, consumer) -> {
            ColorSphere.emit(pose, consumer, center, HALO_RADIUS * strength,
                    ARGB.color(Math.round(HALO_ALPHA * strength), HALO_RGB));
            ColorSphere.emit(pose, consumer, center, CORE_RADIUS * strength,
                    ARGB.color(Math.round(CORE_ALPHA * strength), CORE_RGB));
        });
    }

    /**
     * How much of the mote shows at a fade stage: all of it fresh, less at each stage after.
     *
     * @param fade the wisp's fade stage
     * @return the share, one fresh down to a quarter at the last stage
     */
    static float strength(int fade) {
        return 1f - (float) fade / (WispBlock.LAST_FADE + 1);
    }

    /** Render state snapshot for a wisp: its fade stage and its bob clock. */
    public static class WispRenderState extends BlockEntityRenderState {
        /** The wisp's fade stage. */
        public int fade;
        /** The bob's clock in ticks, its phase spread by position. */
        public float time;
    }
}

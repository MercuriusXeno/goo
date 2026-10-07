package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Renders a locked petal's item into a picture of its own, then draws that
 * picture across the quads its {@link CutItemRenderState} carries rather
 * than as the whole square, so the item shows only under its petal's face.
 * decision icons-slide-in-from-behind-the-tip
 */
public final class CutItemRenderer extends PictureInPictureRenderer<CutItemRenderState> {

    /** Turns the item model the way the GUI's own items render: y up and the depth axis toward the viewer. */
    private static final float FLIP = -1.0f;
    private static final String TEXTURE_LABEL = "goo_cut_item";
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final float HALF = 0.5f;

    private CutItemRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    /**
     * Registers the renderer for every {@link CutItemRenderState} the GUI receives.
     *
     * @param event the event instance
     */
    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(CutItemRenderState.class, CutItemRenderer::new);
    }

    @Override
    public Class<CutItemRenderState> getRenderStateClass() {
        return CutItemRenderState.class;
    }

    @Override
    protected void renderToTexture(CutItemRenderState renderState, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackingItemStackRenderState item = new TrackingItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(item, renderState.stack(), ItemDisplayContext.GUI,
                minecraft.level, minecraft.player, 0);
        poseStack.scale(1.0f, FLIP, FLIP);
        minecraft.gameRenderer.getLighting().setupFor(item.usesBlockLight()
                ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage storage = dispatcher.getSubmitNodeStorage();
        item.submit(poseStack, storage, GooSubmitter.fullbrightLight(), OverlayTexture.NO_OVERLAY, 0);
        dispatcher.renderAllFeatures();
    }

    /**
     * Draws the picture across the cut quads on the item's own layer. The
     * picture's texture is private to the base class, so the whole-square
     * blit the base builds is caught to read it, and never drawn.
     *
     * @param renderState    the item and its cut
     * @param guiRenderState the frame's GUI state, its current layer the item's
     */
    @Override
    protected void blitTexture(CutItemRenderState renderState, GuiRenderState guiRenderState) {
        BlitCatcher catcher = new BlitCatcher();
        super.blitTexture(renderState, catcher);
        TextureSetup picture = catcher.caught();
        if (picture == null) {
            return;
        }
        guiRenderState.addGlyphToCurrentLayer(new PetalRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                picture, renderState.pose(), flipped(renderState.cut()), OPAQUE_WHITE, renderState.scissorArea(),
                renderState.bounds()));
    }

    /**
     * The cut's corners sampling the picture, which the GPU holds bottom row
     * first, as the base class's own blit reads it with v running 1 to 0.
     *
     * @param cut the cut's corners, v 0 at the square's top
     * @return the corners with v turned over
     */
    static List<PetalRenderState.ScreenVertex> flipped(List<PetalRenderState.ScreenVertex> cut) {
        return cut.stream().map(corner -> new PetalRenderState.ScreenVertex(corner.x(), corner.y(), corner.u(),
                1.0f - corner.v())).toList();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height * HALF;
    }

    @Override
    protected String getTextureLabel() {
        return TEXTURE_LABEL;
    }

    /** A GUI state that keeps the texture of the one blit added to it and draws nothing. */
    private static final class BlitCatcher extends GuiRenderState {
        private @Nullable TextureSetup caught;

        @Override
        public void addBlitToCurrentLayer(BlitRenderState blitState) {
            caught = blitState.textureSetup();
        }

        @Nullable TextureSetup caught() {
            return caught;
        }
    }
}

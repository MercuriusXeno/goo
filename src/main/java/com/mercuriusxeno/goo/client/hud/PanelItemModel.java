package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ItemSubmitCollector;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws an item's model into an in-world HUD panel as the inventory draws it: the GUI
 * display transform, lit by the GUI's item lights, at the panel's fade.
 * decision all-huds-draw-an-items-model-as-the-inventory-does
 */
final class PanelItemModel {

    /** Reused render state; the HUD paints on the render thread alone. */
    private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();
    /**
     * Depth in scaled pixels the model's unit cube squashes into, so the whole model stands
     * in front of the panel's background, whose depth is already written.
     */
    private static final float MODEL_DEPTH = 1f;
    /** Divisor for centering. */
    private static final float HALF = 2f;
    /** Turns the GUI's upward Y into the panel's downward pixels, as the GUI's own item pose does. */
    private static final float GUI_Y_FLIP = -1f;

    private PanelItemModel() {
    }

    /**
     * Draws the item's model in an icon slot of the panel and flushes it under the GUI's
     * item lights, then hands the level its own lights back.
     *
     * @param poseStack the panel's pose stack, in scaled pixels with Y running down
     * @param buffers   the buffer source, flushed of the panel's other draws
     * @param item      the item's registry id
     * @param slot      the icon slot's top left and the fade the model draws at
     */
    static void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Identifier item,
                     IconSlot slot) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(item).getDefaultInstance();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemModelResolver().updateForTopItem(
                RENDER_STATE, stack, ItemDisplayContext.GUI, minecraft.level, null, 0);
        if (RENDER_STATE.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(slot.x() + PanelPainter.ICON_SIZE / HALF, slot.y() + PanelPainter.ICON_SIZE / HALF,
                InWorldHud.CONTENT_Z);
        poseStack.scale(PanelPainter.ICON_SIZE, PanelPainter.ICON_SIZE, MODEL_DEPTH);
        PanelCollector collector = new PanelCollector(buffers, new Matrix4f(poseStack.last().pose()),
                PanelPainter.fadeColor(InWorldHud.OPAQUE_WHITE, slot.opacity()));
        poseStack.popPose();
        PoseStack guiSpace = new PoseStack();
        guiSpace.scale(1f, GUI_Y_FLIP, 1f);
        RENDER_STATE.submit(guiSpace, collector, GooSubmitter.fullbrightLight(), OverlayTexture.NO_OVERLAY, 0);
        Lighting lighting = minecraft.gameRenderer.getLighting();
        lighting.setupFor(RENDER_STATE.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        buffers.endBatch();
        lighting.setupFor(Lighting.Entry.LEVEL);
    }

    /**
     * Where an item model draws in its row and the fade it draws at.
     *
     * @param x       the icon slot's left X
     * @param y       the icon slot's top Y
     * @param opacity the panel's fade [0, 1]
     */
    record IconSlot(float x, float y, float opacity) {
    }

    /**
     * Writes an item model's features straight into the panel's buffers: positions through the
     * panel's pose, normals in the GUI's own space, so the GUI's item lights shade the model as the
     * inventory does whichever way the panel faces. Baked quads draw translucent at the panel's fade;
     * a special model (a chest, a shield) draws on its own render type, where an opaque one holds its
     * alpha whole. The enchantment glint is left undrawn.
     */
    private static final class PanelCollector extends ItemSubmitCollector {
        private final MultiBufferSource buffers;
        private final Matrix4f panelPose;
        private final int fadeColor;

        PanelCollector(MultiBufferSource buffers, Matrix4f panelPose, int fadeColor) {
            this.buffers = buffers;
            this.panelPose = panelPose;
            this.fadeColor = fadeColor;
        }

        /**
         * @param itemPose the pose the model submitted at, in GUI space
         * @return a stack whose pose places the model in the panel and whose normals stay in GUI space
         */
        private PoseStack inPanel(PoseStack itemPose) {
            PoseStack placed = new PoseStack();
            PoseStack.Pose last = placed.last();
            last.pose().set(panelPose).mul(itemPose.last().pose());
            last.normal().set(itemPose.last().normal());
            return placed;
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
                               int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                               ItemStackRenderState.FoilType foilType) {
            PoseStack.Pose pose = inPanel(poseStack).last();
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(overlayCoords);
            for (BakedQuad quad : quads) {
                instance.setColor(ARGB.multiply(fadeColor, tintOf(quad, tintLayers)));
                buffers.getBuffer(GooSubmitter.translucentOn(quad.materialInfo().sprite().atlasLocation()))
                        .putBakedQuad(pose, quad, instance);
            }
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                    int lightCoords, int overlayCoords, int tintedColor,
                                    @Nullable TextureAtlasSprite sprite, int outlineColor,
                                    ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
            VertexConsumer buffer = buffers.getBuffer(renderType);
            model.setupAnim(state);
            model.renderToBuffer(inPanel(poseStack), sprite == null ? buffer : sprite.wrap(buffer),
                    lightCoords, overlayCoords, ARGB.multiply(fadeColor, tintedColor));
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                         CustomGeometryRenderer renderer) {
            renderer.render(inPanel(poseStack).last(), buffers.getBuffer(renderType));
        }

        /**
         * @param quad       the baked quad
         * @param tintLayers per-tint-index colors from the item color handler
         * @return the quad's tint color, or opaque white when it takes none
         */
        private static int tintOf(BakedQuad quad, int[] tintLayers) {
            int tintIndex = quad.materialInfo().tintIndex();
            boolean tinted = quad.materialInfo().isTinted() && tintIndex >= 0 && tintIndex < tintLayers.length;
            return tinted ? tintLayers[tintIndex] : InWorldHud.OPAQUE_WHITE;
        }
    }
}

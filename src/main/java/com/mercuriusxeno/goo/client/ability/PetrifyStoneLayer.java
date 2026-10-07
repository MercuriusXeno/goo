package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;

/**
 * Draws a petrifying mob turning to stone: its model again in a stone
 * texture, laid in noise patches that spread and grow together as its petrify
 * gauge fills, covering it whole at a statue
 * (decision petrify-stone-encasement-and-calcify-map).
 *
 * @param <S> the render state type
 * @param <M> the model type
 */
public final class PetrifyStoneLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the share of the mob turned to stone this frame. */
    public static final ContextKey<Float> PETRIFIED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "petrified"));

    /** A whole statue's share. */
    public static final float WHOLE = 1f;

    /** Draw order after the mob's model and its coat. */
    private static final int STONE_ORDER = 2;
    private static final int NO_OUTLINE = 0;
    private static final int STONE_GREY = 0xFFFFFF;

    /**
     * @param parent the living entity renderer the layer draws over
     */
    public PetrifyStoneLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    /**
     * Adds the stone layer to a renderer that draws a living entity; any other
     * renderer is left as it is.
     *
     * @param renderer the renderer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new PetrifyStoneLayer<>(living));
        }
    }

    /**
     * Stamps the share of the entity turned to stone onto its render state, read off its synced gauge.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampPetrify(Entity entity, EntityRenderState state) {
        if (entity.hasData(GooAttachments.PETRIFICATION)) {
            state.setRenderData(PETRIFIED, entity.getData(GooAttachments.PETRIFICATION).share());
        }
    }

    /**
     * The color the stone's vertices carry: white, the share riding the alpha.
     *
     * @param share the share turned to stone, 0 to 1
     * @return the ARGB color
     */
    static int shareColor(float share) {
        return ARGB.color(ARGB.as8BitChannel(Math.clamp(share, 0f, 1f)), STONE_GREY);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        float share = state.getRenderDataOrDefault(PETRIFIED, 0f);
        if (state.isInvisible || share <= 0f) {
            return;
        }
        submitNodeCollector.order(STONE_ORDER).submitModel(getParentModel(), state, poseStack,
                GooRenderTypes.PETRIFY_STONE_TYPE, lightCoords, OverlayTexture.NO_OVERLAY, shareColor(share), null,
                NO_OUTLINE, null);
    }
}

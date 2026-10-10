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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;

/**
 * Draws a mob being encased: its model, and the shell it wears over it such
 * as a sheep's wool, again in a block texture flush on every face, laid in
 * noise patches that spread and grow together as its gauge fills, covering
 * it whole when full. Petrify's stone and frost's ice are the two
 * encasements, each its own layer reading its own share.
 * Decisions petrify-stone-encasement-and-calcify-map, frozen-gauge-per-mob-encases-when-full.
 *
 * @param <S> the render state type
 * @param <M> the model type
 */
public final class EncasementLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the share of the mob turned to stone this frame. */
    public static final ContextKey<Float> PETRIFIED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "petrified"));

    /** The render data carrying the share of the mob frozen this frame. */
    public static final ContextKey<Float> FROZEN = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "frozen"));

    /** The render data carrying the seed the mob's encasement spreads its pattern from. */
    public static final ContextKey<Integer> SEED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "encasement_seed"));

    /** A whole encasement's share. */
    public static final float WHOLE = 1f;

    /** Draw order after the mob's model and its coat. */
    private static final int ENCASEMENT_ORDER = 2;
    private static final int NO_OUTLINE = 0;
    /** The color's red, green and blue, which carry the seed to the shader, the share riding the alpha. */
    private static final int SEED_BITS = 0xFFFFFF;
    /** Mixes an entity id before it becomes a seed. */
    private static final int SEED_MIX = 0x9E3779B1;

    /** The outer shell the mob shows over its body, which the encasement covers too while it shows. */
    private final MobShells.Shell shell;
    /** The share this layer reads off the render state. */
    private final ContextKey<Float> shareKey;
    /** The render type sampling this encasement's texture. */
    private final RenderType renderType;

    /**
     * @param parent     the living entity renderer the layer draws over
     * @param shell      the outer shell the mob wears, or MobShells.NONE
     * @param shareKey   the render data carrying this encasement's share
     * @param renderType the render type sampling its texture
     */
    public EncasementLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell, ContextKey<Float> shareKey,
                           RenderType renderType) {
        super(parent);
        this.shell = shell;
        this.shareKey = shareKey;
        this.renderType = renderType;
    }

    /**
     * Adds the stone and the frost layers to a renderer that draws a living
     * entity; any other renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new EncasementLayer<>(living, shell, PETRIFIED, GooRenderTypes.PETRIFY_STONE_TYPE));
            living.addLayer(new EncasementLayer<>(living, shell, FROZEN, GooRenderTypes.FROST_ICE_TYPE));
        }
    }

    /**
     * Stamps the share of the entity turned to stone onto its render state, read off its synced gauge.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampPetrify(Entity entity, EntityRenderState state) {
        state.setRenderData(SEED, seedOf(entity.getId()));
        if (entity.hasData(GooAttachments.PETRIFICATION)) {
            state.setRenderData(PETRIFIED, entity.getData(GooAttachments.PETRIFICATION).share());
        }
    }

    /**
     * Stamps the share of the entity frozen onto its render state, read off its synced gauge.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampFrozen(Entity entity, EntityRenderState state) {
        state.setRenderData(SEED, seedOf(entity.getId()));
        if (entity.hasData(GooAttachments.FROZEN)) {
            state.setRenderData(FROZEN, entity.getData(GooAttachments.FROZEN).gauge());
        }
    }

    /**
     * The color the encasement's vertices carry: the mob's seed in red, green
     * and blue, which the shader offsets its pattern by, the share riding the
     * alpha (decision frozen-gauge-per-mob-encases-when-full).
     *
     * @param share the share encased, 0 to 1
     * @param seed  the mob's seed
     * @return the ARGB color
     */
    static int shareColor(float share, int seed) {
        return ARGB.color(ARGB.as8BitChannel(Math.clamp(share, 0f, 1f)), seed & SEED_BITS);
    }

    /**
     * The seed a mob's encasement spreads its pattern from: its entity id,
     * hashed so neighbouring ids seed far apart.
     *
     * @param entityId the entity's id
     * @return the seed, 24 bits
     */
    static int seedOf(int entityId) {
        return Integer.reverse(entityId * SEED_MIX) & SEED_BITS;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        float share = state.getRenderDataOrDefault(shareKey, 0f);
        if (state.isInvisible || share <= 0f) {
            return;
        }
        submitEncasement(submitNodeCollector, getParentModel(), state, poseStack, lightCoords, share);
        EntityModel<?> shown = shell.shown(state);
        if (shown != null) {
            submitShellEncasement(submitNodeCollector, shown, state, poseStack, lightCoords, share);
        }
    }

    @SuppressWarnings("unchecked")
    private void submitShellEncasement(SubmitNodeCollector collector, EntityModel<?> shown, S state,
                                       PoseStack poseStack, int lightCoords, float share) {
        submitEncasement(collector, (EntityModel<? super S>) shown, state, poseStack, lightCoords, share);
    }

    private void submitEncasement(SubmitNodeCollector collector, EntityModel<? super S> model, S state,
                                  PoseStack poseStack, int lightCoords, float share) {
        collector.order(ENCASEMENT_ORDER).submitModel(model, state, poseStack, renderType,
                lightCoords, OverlayTexture.NO_OVERLAY, shareColor(share, state.getRenderDataOrDefault(SEED, 0)), null,
                NO_OUTLINE, null);
    }
}

package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
 * Draws the vines rooting a mob: its model, and the shell it wears over it
 * such as a sheep's wool, again in the vine texture flush on every face, in
 * noise patches that spread over it as the blob unpacks and thin away as the
 * vines fall once released; the tendrils down to the root are
 * {@link VineTendrils}'.
 * vines-unpack-root-and-thorn
 *
 * @param <S> the render state type
 * @param <M> the model type
 */
public final class VineTangleLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the share of the mob the vines cover this frame. */
    public static final ContextKey<Float> TANGLED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "tangled"));

    /** The green vanilla tints a vine with outside any biome's foliage. */
    public static final int VINE_GREEN = 0x48B518;

    /** Draw order after the mob's model, its coat and any stone. */
    private static final int TANGLE_ORDER = 3;
    private static final int NO_OUTLINE = 0;

    /** The outer shell the mob shows over its body, which the vines cover too while it shows. */
    private final MobShells.Shell shell;

    /**
     * @param parent the living entity renderer the layer draws over
     * @param shell  the outer shell the mob wears, or MobShells.NONE
     */
    public VineTangleLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell) {
        super(parent);
        this.shell = shell;
    }

    /**
     * Adds the tangle layer to a renderer that draws a living entity; any
     * other renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new VineTangleLayer<>(living, shell));
        }
    }

    /**
     * Stamps the share of the entity the vines cover this frame onto its
     * render state, read off its synced vines.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampTangle(Entity entity, EntityRenderState state) {
        if (!entity.hasData(GooAttachments.ROOTED)) {
            return;
        }
        Rooted rooted = entity.getData(GooAttachments.ROOTED);
        float time = entity.level().getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        state.setRenderData(TANGLED, rooted.coverAt(time));
    }

    /**
     * The color the vines' vertices carry: the vine green, the share riding the alpha.
     *
     * @param share the share the vines cover, 0 to 1
     * @return the ARGB color
     */
    static int shareColor(float share) {
        return ARGB.color(ARGB.as8BitChannel(Math.clamp(share, 0f, 1f)), VINE_GREEN);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        float share = state.getRenderDataOrDefault(TANGLED, 0f);
        if (state.isInvisible || share <= 0f) {
            return;
        }
        submitTangle(submitNodeCollector, getParentModel(), state, poseStack, lightCoords, share);
        EntityModel<?> shown = shell.shown(state);
        if (shown != null) {
            submitShellTangle(submitNodeCollector, shown, state, poseStack, lightCoords, share);
        }
    }

    @SuppressWarnings("unchecked")
    private void submitShellTangle(SubmitNodeCollector collector, EntityModel<?> shown, S state,
                                   PoseStack poseStack, int lightCoords, float share) {
        submitTangle(collector, (EntityModel<? super S>) shown, state, poseStack, lightCoords, share);
    }

    private void submitTangle(SubmitNodeCollector collector, EntityModel<? super S> model, S state,
                              PoseStack poseStack, int lightCoords, float share) {
        collector.order(TANGLE_ORDER).submitModel(model, state, poseStack, GooRenderTypes.VINE_TANGLE_TYPE,
                lightCoords, OverlayTexture.NO_OVERLAY, shareColor(share), null, NO_OUTLINE, null);
    }
}

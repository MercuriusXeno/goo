package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mercuriusxeno.goo.ability.yarn.Tangled;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;

/**
 * Draws what tangles a mob over it: the vines rooting it and the web strands
 * Stitch tangles it in, each its model, and the shell it wears over it such
 * as a sheep's wool, again in its own texture flush on every face, in noise
 * patches that spread over it as the tangle takes hold and thin away as it
 * lets go; the vines' tendrils down to the root are {@link VineTendrils}'.
 * vines-unpack-root-and-thorn
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 *
 * @param <S> the render state type
 * @param <M> the model type
 */
public final class TangleLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the share of the mob the vines cover this frame. */
    public static final ContextKey<Float> TANGLED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "tangled"));

    /** The render data carrying the share of the mob Stitch's web strands cover this frame. */
    public static final ContextKey<Float> STRANDED = new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID,
            "stranded"));

    /** The green vanilla tints a vine with outside any biome's foliage. */
    public static final int VINE_GREEN = 0x48B518;

    /** The web's own white, the cobweb texture drawn untinted. */
    private static final int WEB_WHITE = 0xFFFFFF;

    /** Draw order after the mob's model, its coat and any stone. */
    private static final int TANGLE_ORDER = 3;
    private static final int NO_OUTLINE = 0;

    /**
     * One kind of tangle the layer draws.
     *
     * @param share the render data carrying the share it covers
     * @param type  the render type drawing its texture over the model
     * @param tint  the RGB its texture is tinted by
     */
    private record Wrap(ContextKey<Float> share, RenderType type, int tint) {
    }

    private static final Wrap VINES = new Wrap(TANGLED, GooRenderTypes.VINE_TANGLE_TYPE, VINE_GREEN);
    private static final Wrap WEB = new Wrap(STRANDED, GooRenderTypes.WEB_TANGLE_TYPE, WEB_WHITE);

    /** The outer shell the mob shows over its body, which the tangle covers too while it shows. */
    private final MobShells.Shell shell;
    /** The tangle this layer draws. */
    private final Wrap wrap;

    private TangleLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell, Wrap wrap) {
        super(parent);
        this.shell = shell;
        this.wrap = wrap;
    }

    /**
     * Adds the vine and web tangle layers to a renderer that draws a living
     * entity; any other renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new TangleLayer<>(living, shell, VINES));
            living.addLayer(new TangleLayer<>(living, shell, WEB));
        }
    }

    /**
     * Stamps the shares of the entity its vines and its web strands cover
     * this frame onto its render state, read off their synced attachments.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampTangle(Entity entity, EntityRenderState state) {
        boolean rooted = entity.hasData(GooAttachments.ROOTED);
        boolean tangled = entity.hasData(GooAttachments.TANGLED);
        if (!rooted && !tangled) {
            return;
        }
        float time = entity.level().getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (rooted) {
            Rooted vines = entity.getData(GooAttachments.ROOTED);
            state.setRenderData(TANGLED, vines.coverAt(time));
        }
        if (tangled) {
            Tangled web = entity.getData(GooAttachments.TANGLED);
            state.setRenderData(STRANDED, web.coverAt(time));
        }
    }

    /**
     * The color a tangle's vertices carry: its tint, the share riding the alpha.
     *
     * @param share the share the tangle covers, 0 to 1
     * @param tint  the tangle's RGB
     * @return the ARGB color
     */
    private static int shareColor(float share, int tint) {
        return ARGB.color(ARGB.as8BitChannel(Math.clamp(share, 0f, 1f)), tint);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        float share = state.getRenderDataOrDefault(wrap.share(), 0f);
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
        collector.order(TANGLE_ORDER).submitModel(model, state, poseStack, wrap.type(),
                lightCoords, skinSizeCoords(model.root()), shareColor(share, wrap.tint()), null, NO_OUTLINE, null);
    }

    /**
     * The model's skin size packed in the overlay coordinates, which the vine
     * shader reads in place of an overlay, the width in u and the height in v.
     * Decision diagnose-and-correct-vines-and-ailment-overlay-assuming-a-square-skin.
     *
     * @param root the root part of the model the tangle draws over
     * @return the skin size as overlay coordinates
     */
    static int skinSizeCoords(ModelPart root) {
        return ModelSkinSizeCache.sizeOf(root).overlayCoords();
    }
}

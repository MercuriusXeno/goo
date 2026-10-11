package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
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
import java.util.List;
import java.util.stream.Stream;

/**
 * A status ailment as a render layer: one layer on every living entity
 * renderer draws the model again through the ailment overlay pipeline once
 * per ailment the entity wears, under that ailment's color and pattern, and
 * again over the outer shell it shows, such as a slime's gel or a sheep's
 * wool, so the shell never buries the overlay.
 * Decision ailment-overlay-shader-per-ailment.
 *
 * @param <S> the renderer's state
 * @param <M> the renderer's model
 */
public final class AilmentOverlayLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the ailments an entity wears this frame, each with its strength. */
    public static final ContextKey<List<StampedAilment>> AILMENTS =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ailments"));

    /** A color channel's full value. */
    private static final int MAX_CHANNEL = 255;

    /** Draw order after the mob's own model, its vanilla layers and its goo splats. */
    private static final int OVERLAY_ORDER = 2;

    /** No outline: the overlay is the ailment's whole look. */
    private static final int NO_OUTLINE = 0;

    /** The outer shell the mob shows over its body, which the overlay covers too while it shows. */
    private final MobShells.Shell shell;

    /**
     * @param parent the living entity renderer the layer draws over
     * @param shell  the outer shell its mob wears, or MobShells.NONE
     */
    public AilmentOverlayLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell) {
        super(parent);
        this.shell = shell;
    }

    /**
     * Adds an ailment overlay layer to a renderer that draws a living
     * entity; any other renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new AilmentOverlayLayer<>(living, shell));
        }
    }

    /**
     * One ailment as a frame draws it.
     *
     * @param kind     the ailment
     * @param strength how strongly the overlay draws, 0 to 1
     */
    public record StampedAilment(AilmentKind kind, float strength) {
    }

    /**
     * Stamps the ailments the entity wears onto its render state, each with
     * its strength this frame, read by the layer when it draws; a charmed
     * mob wears the hex glisten from its synced charm.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampAilments(Entity entity, EntityRenderState state) {
        long tick = entity.level().getGameTime();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<StampedAilment> worn = MobAilments.CLIENT.ailmentsOf(entity.getId(), tick).stream()
                .map(ailment -> new StampedAilment(ailment.kind(),
                        MobAilments.strength(ailment.ticksLeft() - partialTick)))
                .toList();
        state.setRenderData(AILMENTS, withCharmGlisten(worn, entity.hasData(GooAttachments.CHARMED)));
    }

    /**
     * The ailments a frame draws once the charm is counted: a charmed mob
     * wears the hex glisten whole for as long as the charm holds, in place
     * of any timed hex overlay it wears.
     * charm-holds-until-struck
     *
     * @param worn    the timed ailments the entity wears
     * @param charmed whether the entity holds a charm
     * @return the ailments to draw
     */
    static List<StampedAilment> withCharmGlisten(List<StampedAilment> worn, boolean charmed) {
        if (!charmed) {
            return worn;
        }
        return Stream.concat(worn.stream().filter(ailment -> ailment.kind() != AilmentKind.HEX),
                Stream.of(new StampedAilment(AilmentKind.HEX, 1f))).toList();
    }

    /**
     * The color the overlay's vertices carry: the ailment's color with its
     * strength, scaled by the ailment's opacity, riding the alpha.
     *
     * @param kind     the ailment
     * @param strength how strongly it draws, 0 to 1
     * @return the ARGB color
     */
    static int overlayColor(AilmentKind kind, float strength) {
        return ARGB.color(Math.round(strength * kind.opacity() * MAX_CHANNEL), kind.rgb());
    }

    /**
     * The overlay coordinates that carry the ailment's pattern to the shader,
     * its ordinal in U.
     *
     * @param kind the ailment
     * @return the packed overlay coordinates
     */
    static int patternCoords(AilmentKind kind) {
        return OverlayTexture.pack(kind.pattern().ordinal(), 0);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        EntityModel<?> shown = shell.shown(state);
        for (StampedAilment ailment : state.getRenderDataOrDefault(AILMENTS, List.<StampedAilment>of())) {
            submitOverlay(submitNodeCollector, getParentModel(), state, poseStack, lightCoords, ailment);
            if (shown != null) {
                submitShellOverlay(submitNodeCollector, shown, state, poseStack, lightCoords, ailment);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void submitShellOverlay(SubmitNodeCollector collector, EntityModel<?> shown, S state,
                                    PoseStack poseStack, int lightCoords, StampedAilment ailment) {
        submitOverlay(collector, (EntityModel<? super S>) shown, state, poseStack, lightCoords, ailment);
    }

    private void submitOverlay(SubmitNodeCollector collector, EntityModel<? super S> model, S state,
                               PoseStack poseStack, int lightCoords, StampedAilment ailment) {
        collector.order(OVERLAY_ORDER).submitModel(model, state, poseStack, GooRenderTypes.GOO_AILMENT_OVERLAY_TYPE,
                lightCoords, patternCoords(ailment.kind()), overlayColor(ailment.kind(), ailment.strength()), null,
                NO_OUTLINE, null);
    }
}

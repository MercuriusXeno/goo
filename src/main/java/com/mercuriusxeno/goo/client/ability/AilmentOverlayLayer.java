package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * A status ailment as a render layer: one layer on every living entity
 * renderer draws the model again through the ailment overlay pipeline once
 * per ailment field the entity wears, under that ailment's color and
 * pattern, and again over the outer shell it shows, such as a slime's gel or
 * a sheep's wool, so the shell never buries the overlay. An ailment marking
 * its mob instead outlines it in the pulsing outline in its color.
 * Decision ailment-overlay-shader-per-ailment.
 * Decision ailment-glisten-becomes-a-pulsing-outline.
 *
 * @param <S> the renderer's state
 * @param <M> the renderer's model
 */
public final class AilmentOverlayLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the ailments an entity wears this frame, each with its strength. */
    public static final ContextKey<List<StampedAilment>> AILMENTS =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ailments"));

    /** The strength an ailment with no timer draws at. */
    private static final float FULL_STRENGTH = 1f;

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
     * its strength this frame, read by the layer when it draws, and outlines
     * the entity in the pulsing outline of an outlined ailment it wears; a
     * charmed mob wears the hex outline from its synced charm. Stamped after
     * Rally's outline and before the glove's aim outline, so an ailment's
     * outline stands over the red and the aim's over both.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampAilments(Entity entity, EntityRenderState state) {
        long tick = entity.level().getGameTime();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<StampedAilment> worn = drawn(MobAilments.CLIENT.ailmentsOf(entity.getId(), tick), partialTick);
        List<StampedAilment> stamped = withCharmOutline(worn, entity.hasData(GooAttachments.CHARMED));
        state.setRenderData(AILMENTS, stamped);
        outlineOf(stamped, tick + partialTick, ClientGooTypes::highlight)
                .ifPresent(color -> state.outlineColor = color);
    }

    /**
     * The ailments a frame draws of those an entity wears, each at its
     * strength this frame; an ailment drawing neither a field nor an
     * outline, as Grab's flail does, is left out.
     *
     * @param worn        the ailments the entity wears
     * @param partialTick the fraction of the tick elapsed
     * @return the overlays to draw
     */
    static List<StampedAilment> drawn(List<MobAilments.Worn> worn, float partialTick) {
        return worn.stream()
                .filter(ailment -> ailment.kind().drawsOverlay() || ailment.kind().drawsOutline())
                .map(ailment -> new StampedAilment(ailment.kind(),
                        MobAilments.strength(ailment.ticksLeft() - partialTick)))
                .toList();
    }

    /**
     * The ailments a frame draws once the charm is counted: a charmed mob
     * wears the hex outline whole for as long as the charm holds, in place
     * of any timed hex it wears.
     * charm-holds-until-struck
     *
     * @param worn    the timed ailments the entity wears
     * @param charmed whether the entity holds a charm
     * @return the ailments to draw
     */
    static List<StampedAilment> withCharmOutline(List<StampedAilment> worn, boolean charmed) {
        if (!charmed) {
            return worn;
        }
        return Stream.concat(worn.stream().filter(ailment -> ailment.kind() != AilmentKind.HEX),
                Stream.of(new StampedAilment(AilmentKind.HEX, FULL_STRENGTH))).toList();
    }

    /**
     * The outline a frame draws of the ailments stamped: the pulsing outline
     * in the color of the strongest outlined ailment, the first of equals.
     *
     * @param stamped   the ailments the frame draws
     * @param gameTime  the game time including the partial tick
     * @param typeColor answers a goo type's color, for a type-tinted kind such as Scry's astral
     * @return the ARGB outline color, empty where no outlined ailment stands
     */
    static OptionalInt outlineOf(List<StampedAilment> stamped, float gameTime,
                                 ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        return stamped.stream()
                .filter(ailment -> ailment.kind().drawsOutline())
                .reduce((strongest, next) -> next.strength() > strongest.strength() ? next : strongest)
                .map(ailment -> OptionalInt.of(PulsingOutline.color(ailment.kind().rgb(typeColor), gameTime)))
                .orElse(OptionalInt.empty());
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
        return overlayColor(kind, strength, ClientGooTypes::highlight);
    }

    /**
     * The overlay color with the goo type colors read through the resolver
     * given, so a type-tinted kind takes its type's color (decision scry-ships-on-astral).
     *
     * @param kind      the ailment
     * @param strength  how strongly it draws, 0 to 1
     * @param typeColor answers a goo type's color
     * @return the ARGB color
     */
    static int overlayColor(AilmentKind kind, float strength,
                            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        return ARGB.color(Math.round(strength * kind.opacity() * MAX_CHANNEL), kind.rgb(typeColor));
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
            if (ailment.kind().drawsOverlay()) {
                submitOverlay(submitNodeCollector, getParentModel(), state, poseStack, lightCoords, ailment);
                if (shown != null) {
                    submitShellOverlay(submitNodeCollector, shown, state, poseStack, lightCoords, ailment);
                }
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

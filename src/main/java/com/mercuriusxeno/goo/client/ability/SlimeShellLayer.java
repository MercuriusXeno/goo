package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
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
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The slime shell a charm-like cast's dart leaves, as a mob render layer:
 * the mob's body drawn again, stood off its skin and translucent in the goo's
 * color, as a slime's outer jelly wraps its core, below the shell's melt line.
 * A witch's shell draws over her body with the bare hat rim hidden.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 * diagnose-and-correct-the-floating-hat-brim-box
 *
 * @param <S> the renderer's state
 * @param <M> the renderer's model
 */
public final class SlimeShellLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the shell the mob wears this frame. */
    public static final ContextKey<SlimeShells.Worn> SHELL =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "slime_shell"));

    /** The white the shell's color tints. */
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(Goo.MODID, "textures/misc/white.png");
    /** How opaque the shell draws where it stands whole: the core shows through, as through a slime's jelly. */
    private static final float OPACITY = 0.55f;
    /** Draw order after the mob's own model, its vanilla layers, its splats and its ailments. */
    private static final int SHELL_ORDER = 3;
    /** Opaque white, the color a model draw takes before ShellVertices writes the goo's own. */
    private static final int UNTINTED = 0xFFFFFFFF;
    /** The goo's color without its alpha. */
    private static final int RGB_MASK = 0xFFFFFF;

    private final MobShells.Shell shell;
    private final @Nullable EntityModel<?> body;

    /**
     * @param parent the living entity renderer the layer draws over
     * @param shell  the outer shell the mob wears, or MobShells.NONE
     * @param body   the body the slime wraps, from EncasedBodies, or null for the renderer's own model
     */
    public SlimeShellLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell, @Nullable EntityModel<?> body) {
        super(parent);
        this.shell = shell;
        this.body = body;
    }

    /**
     * Adds a slime shell layer to a renderer that draws a living entity; any
     * other renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     * @param body     the body the slime wraps, from EncasedBodies, or null for the renderer's own model
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell, @Nullable EntityModel<?> body) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new SlimeShellLayer<>(living, shell, body));
        }
    }

    /**
     * Stamps the shell the entity wears onto its render state, read by the
     * layer when it draws.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampShell(Entity entity, EntityRenderState state) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        state.setRenderData(SHELL, SlimeShells.CLIENT.shellOf(entity.getId(),
                entity.level().getGameTime() + partialTick).orElse(null));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        SlimeShells.Worn worn = state.getRenderData(SHELL);
        if (worn == null || state.isInvisible) {
            return;
        }
        EntityModel<? super S> model = (EntityModel<? super S>) MobCoatLayer.surface(shell.shown(state), body,
                getParentModel());
        // the goo's own color: a fluid tint answers white over a goo whose fluid texture carries its color
        int rgb = ClientGooTypes.color(worn.gooType()) & RGB_MASK;
        submitNodeCollector.order(SHELL_ORDER).submitCustomGeometry(poseStack, GooSubmitter.translucentOn(WHITE),
                (pose, buffer) -> {
                    PoseStack modelPose = new PoseStack();
                    modelPose.last().set(pose);
                    // The model is shared across every mob of the type, so it is posed for this one at draw time.
                    model.setupAnim(state);
                    float[] heights = crownAndFeet(EncasedBodies.drawnCubes(model));
                    model.renderToBuffer(modelPose, new ShellVertices(buffer, new Matrix4f(pose.pose()).invert(),
                            heights[0], heights[1], worn.melted(), rgb, OPACITY),
                            lightCoords, OverlayTexture.NO_OVERLAY, UNTINTED);
                });
    }

    /**
     * The posed model's crown and feet in its root space, which is y-down.
     *
     * @param cubes the posed model's cubes
     * @return the crown's y then the feet's y
     */
    static float[] crownAndFeet(List<ModelRay.CubeBox> cubes) {
        float crown = Float.POSITIVE_INFINITY;
        float feet = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (ModelRay.CubeBox cube : cubes) {
            Matrix4f cubeToRoot = new Matrix4f(cube.rootToCube()).invert();
            for (float x : new float[] {cube.min().x(), cube.max().x()}) {
                for (float y : new float[] {cube.min().y(), cube.max().y()}) {
                    for (float z : new float[] {cube.min().z(), cube.max().z()}) {
                        cubeToRoot.transformPosition(x, y, z, corner);
                        crown = Math.min(crown, corner.y);
                        feet = Math.max(feet, corner.y);
                    }
                }
            }
        }
        return new float[] {crown, feet};
    }
}

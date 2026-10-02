package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The goo splat as a mob render layer: one layer on every living entity
 * renderer draws the parent model again through the goo coat pipeline,
 * each vertex carrying its offset from the struck point so the shader paints
 * the goo type's fluid texture over the half block around it alone.
 * Decision shader-coat-on-every-mob-landing.
 *
 * @param <S> the renderer's state
 * @param <M> the renderer's model
 */
public final class MobCoatLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends RenderLayer<S, M> {

    /** The render data carrying the splat a mob wears this frame. */
    public static final ContextKey<MobCoats.Coat> COAT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "mob_coat"));

    /** Draw order after the mob's own model and its vanilla layers. */
    private static final int COAT_ORDER = 1;

    /**
     * @param parent the living entity renderer the layer draws over
     */
    public MobCoatLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    /**
     * Adds a splat layer to a renderer that draws a living entity; any other
     * renderer is left as it is.
     *
     * @param renderer the renderer
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new MobCoatLayer<>(living));
        }
    }

    /**
     * Stamps the splat the entity wears onto its render state, read by the
     * layer when it draws.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampCoat(Entity entity, EntityRenderState state) {
        state.setRenderData(COAT, MobCoats.CLIENT.coatOf(entity.getId(), entity.level().getGameTime()));
    }

    /**
     * The hit point carried into the model root's space: the pose places the
     * model relative to the camera, so its inverse takes the camera-relative
     * hit point home.
     *
     * @param rootPose the model root's pose
     * @param hitPoint the world hit point
     * @param camera   the camera's world position
     * @return the hit point in the model root's space
     */
    static Vector3f pinHit(Matrix4f rootPose, Vec3 hitPoint, Vec3 camera) {
        Vec3 relative = hitPoint.subtract(camera);
        return new Matrix4f(rootPose).invert()
                .transformPosition((float) relative.x, (float) relative.y, (float) relative.z, new Vector3f());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        MobCoats.Coat coat = state.getRenderData(COAT);
        if (coat == null || state.isInvisible) {
            return;
        }
        Matrix4f rootPose = new Matrix4f(poseStack.last().pose());
        if (coat.pinnedHit() == null) {
            coat.pin(pinHit(rootPose, coat.hitPoint(),
                    Minecraft.getInstance().gameRenderer.getMainCamera().position()));
        }
        Vector3f pinnedHit = coat.pinnedHit();
        Matrix4f rootInverse = rootPose.invert();
        TextureAtlasSprite fluid = GooRenderUtil.lookupFluidSprite(coat.gooType());
        int tint = GooSubmitter.fluidTint(coat.gooType());
        M model = getParentModel();
        submitNodeCollector.order(COAT_ORDER).submitCustomGeometry(poseStack,
                GooRenderTypes.gooMobCoat(fluid.atlasLocation()), (pose, buffer) -> {
                    PoseStack modelPose = new PoseStack();
                    modelPose.last().set(pose);
                    // The model is shared across every mob of the type, so it is posed for this one at draw time.
                    model.setupAnim(state);
                    model.renderToBuffer(modelPose,
                            new SplatVertices(buffer, rootInverse, pinnedHit, fluid.getU0(), fluid.getV0()),
                            lightCoords, OverlayTexture.NO_OVERLAY, tint);
                });
    }
}

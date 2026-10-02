package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.List;

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

    /** The render data carrying the splats a mob wears this frame, each with its dissolve. */
    public static final ContextKey<List<StampedSplat>> SPLATS =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "mob_splats"));

    /**
     * Blocks the aim backs off from where it entered the mob's box before it
     * is cast against the model, past any part standing proud of the box.
     */
    static final double AIM_BACKOFF_BLOCKS = 2.0;

    /** A color channel's full value. */
    private static final int MAX_CHANNEL = 255;

    /** Draw order after the mob's own model and its vanilla layers. */
    private static final int COAT_ORDER = 1;

    /** The outer shell the mob shows over its body, which the splat sits on while it shows. */
    private final MobShells.Shell shell;

    /**
     * @param parent the living entity renderer the layer draws over
     * @param shell  the outer shell the mob wears, or MobShells.NONE
     */
    public MobCoatLayer(RenderLayerParent<S, M> parent, MobShells.Shell shell) {
        super(parent);
        this.shell = shell;
    }

    /**
     * Adds a splat layer to a renderer that draws a living entity; any other
     * renderer is left as it is.
     *
     * @param renderer the renderer
     * @param shell    the outer shell its mob wears, or MobShells.NONE
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addTo(EntityRenderer<?, ?> renderer, MobShells.Shell shell) {
        if (renderer instanceof LivingEntityRenderer living) {
            living.addLayer(new MobCoatLayer<>(living, shell));
        }
    }

    /**
     * The model the splat sits on this frame: the shell the mob shows over
     * its body, or its body.
     *
     * @param state the mob's render state
     * @return the model to cast against and paint
     */
    @SuppressWarnings("unchecked")
    private EntityModel<? super S> surfaceOf(S state) {
        EntityModel<?> shown = shell.shown(state);
        return shown == null ? getParentModel() : (EntityModel<? super S>) shown;
    }

    /**
     * One splat as a frame draws it.
     *
     * @param coat     the splat
     * @param dissolve how far it has dissolved this frame, 0 to 1
     */
    public record StampedSplat(MobCoats.Coat coat, float dissolve) {
    }

    /**
     * Stamps the splats the entity wears onto its render state, each with
     * its dissolve this frame, read by the layer when it draws.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampCoat(Entity entity, EntityRenderState state) {
        long tick = entity.level().getGameTime();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        state.setRenderData(SPLATS, MobCoats.CLIENT.coatsOf(entity.getId(), tick).stream()
                .map(coat -> new StampedSplat(coat,
                        MobCoats.Coat.dissolveProgress(tick - coat.hitTick() + partialTick)))
                .toList());
    }

    /**
     * The splat's tint with its dissolve riding the alpha: the shader keeps a
     * fragment only while its reach stays below the alpha, so the alpha falls
     * from whole to nothing as the splat dissolves.
     * Decision splat-holds-then-dissolves-dripping.
     *
     * @param tint     the goo type's opaque fluid tint
     * @param dissolve how far the splat has dissolved, 0 to 1
     * @return the tint with the splat's remaining reach as alpha
     */
    static int tintWithReach(int tint, float dissolve) {
        return ARGB.color(Math.round((1f - dissolve) * MAX_CHANNEL), tint);
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

    /**
     * The aim carried on from where it entered the mob's box onto the posed
     * model: the ray backs off along the aim so a part standing proud of the
     * box, a pig's snout, meets it first, and the splat pins to the part it
     * meets, in that part's own space, so it turns with a turning head; where
     * it meets no cube, the box point pins to the model root.
     *
     * @param cubes        the posed model's cube boxes
     * @param rootPose     the model root's pose
     * @param boxHit       the world point the aim entered the mob's box
     * @param aimDirection the aim's unit direction, zero where none
     * @param camera       the camera's world position
     * @return the pin: the struck part and the point in its space, or the root and the box point
     */
    static ModelRay.PartHit aimedModelHit(List<ModelRay.CubeBox> cubes, Matrix4f rootPose, Vec3 boxHit,
            Vec3 aimDirection, Vec3 camera) {
        ModelRay.PartHit boxPin = new ModelRay.PartHit(null, pinHit(rootPose, boxHit, camera));
        if (aimDirection.lengthSqr() == 0) {
            return boxPin;
        }
        Matrix4f rootInverse = new Matrix4f(rootPose).invert();
        Vector3f direction = rootInverse.transformDirection(
                (float) aimDirection.x, (float) aimDirection.y, (float) aimDirection.z, new Vector3f());
        Vector3f origin = pinHit(rootPose, boxHit.subtract(aimDirection.scale(AIM_BACKOFF_BLOCKS)), camera);
        return ModelRay.firstHit(cubes, origin, direction).orElse(boxPin);
    }

    /**
     * The pinned part's pose under the model root as the model stands posed
     * now: the root's own frame for a pin on no part, or one whose part the
     * model no longer holds.
     *
     * @param root the model's root part, already posed for the mob
     * @param pin  the pin
     * @return the frame the pin's point is measured in
     */
    static Matrix4f pinFrame(ModelPart root, ModelRay.PartHit pin) {
        if (pin.partPath() == null) {
            return new Matrix4f();
        }
        return ModelRay.partPose(root, pin.partPath()).orElseGet(Matrix4f::new);
    }

    /**
     * The splat's pin, cast on its first draw where the aim meets the posed
     * model.
     *
     * @param coat     the splat
     * @param rootPose the model root's pose this frame
     * @param state    the mob's render state
     * @param model    the model the splat sits on, posed for the mob
     * @return the pin
     */
    private ModelRay.PartHit pinOf(MobCoats.Coat coat, Matrix4f rootPose, S state, EntityModel<? super S> model) {
        ModelRay.PartHit pin = coat.pin();
        if (pin == null) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            pin = aimedModelHit(ModelRay.cubesOf(model.root()), rootPose, coat.hitPoint(), coat.aimDirection(),
                    camera);
            Vector3f world = new Matrix4f(rootPose).mul(pinFrame(model.root(), pin))
                    .transformPosition(pin.partPoint(), new Vector3f());
            coat.pin(pin, camera.add(world.x, world.y, world.z),
                    new MobCoats.Stance(new Vec3(state.x, state.y, state.z), state.bodyRot));
        }
        return pin;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state,
            float yRot, float xRot) {
        List<StampedSplat> splats = state.getRenderDataOrDefault(SPLATS, List.of());
        if (state.isInvisible) {
            return;
        }
        // Each splat draws its own pass, so every hit keeps its own point, texture and dissolve.
        for (StampedSplat splat : splats) {
            submitSplat(poseStack, submitNodeCollector, lightCoords, state, splat);
        }
    }

    /**
     * Submits one splat's pass over the model.
     *
     * @param poseStack           the model root's pose
     * @param submitNodeCollector the frame's collector
     * @param lightCoords         the mob's light
     * @param state               the mob's render state
     * @param splat               the splat and its dissolve
     */
    private void submitSplat(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            S state, StampedSplat splat) {
        MobCoats.Coat coat = splat.coat();
        EntityModel<? super S> model = surfaceOf(state);
        // The model is shared across every mob of the type, so it is posed for this one before the cast.
        model.setupAnim(state);
        ModelRay.PartHit pin = pinOf(coat, new Matrix4f(poseStack.last().pose()), state, model);
        TextureAtlasSprite fluid = GooRenderUtil.lookupFluidSprite(coat.gooType());
        int tint = tintWithReach(GooSubmitter.fluidTint(coat.gooType()), splat.dissolve());
        submitNodeCollector.order(COAT_ORDER).submitCustomGeometry(poseStack,
                GooRenderTypes.gooMobCoat(fluid.atlasLocation()), (pose, buffer) -> {
                    PoseStack modelPose = new PoseStack();
                    modelPose.last().set(pose);
                    // Posed again at draw time, as other mobs of the type posed it since; the pinned part's
                    // frame is read from this pose, so the splat turns with the part it struck.
                    model.setupAnim(state);
                    Matrix4f cameraToPin = new Matrix4f(pose.pose()).mul(pinFrame(model.root(), pin)).invert();
                    model.renderToBuffer(modelPose,
                            new SplatVertices(buffer, cameraToPin, pin.partPoint(), fluid.getU0(), fluid.getV0()),
                            lightCoords, OverlayTexture.NO_OVERLAY, tint);
                });
    }
}

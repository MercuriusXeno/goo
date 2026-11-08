package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * In third person the arm holding a goo glove ends where the glove starts: the player model's arm part is hidden
 * and this layer draws that arm again from the shoulder down to the middle of the glove's cuff, in the skin's own
 * arm and sleeve texture and in the arm part's own pose, so the sleeve no longer pokes through the glove body's
 * sides. First person is untouched: the glove item already draws over the arm there.
 * decision diagnose-and-correct-the-arm-through-the-glove
 */
public final class GloveArmLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    /** The middle of every glove body's cuff, in glove model pixels: the cuff spans y 9 to 11. */
    static final float CUFF_MODEL_Y = 10f;

    private static final float PIXELS_PER_BLOCK = 16f;
    private static final float MODEL_CENTER = 0.5f;
    /** Vanilla's ItemInHandLayer turns and offsets the held item from the arm part by these. */
    private static final float HAND_X_TURN_DEGREES = -90f;
    private static final float HAND_Y_TURN_DEGREES = 180f;
    private static final float HAND_OFFSET_X_PX = 1f;
    private static final float HAND_OFFSET_Y_PX = 2f;
    private static final float HAND_OFFSET_Z_PX = -10f;
    private static final float RIGHT_SIDE = 1f;
    private static final float LEFT_SIDE = -1f;
    /** PlayerModel's arm cube spans y -2 (the shoulder's top) to 10 (the hand's end), z -2 to 2. */
    private static final float SHOULDER_TOP_Y = -2f;
    private static final float HAND_END_Y = 10f;
    private static final float ARM_MIN_Z = -2f;
    private static final float ARM_DEPTH = 4f;
    private static final float SLEEVE_GROWTH = 0.25f;
    private static final float SKIN_TEXTURE_SIZE = 64f;
    /** The tint LivingEntityRenderer gives a body drawn see-through to a teammate. */
    private static final int SEE_THROUGH_TINT = 654_311_423;
    private static final int OPAQUE_TINT = -1;
    private static final float NO_WHITE_OVERLAY = 0f;

    /** The player renderers' own models, the ones whose glove arm this layer stands in for; armor models are not. */
    private static final Set<PlayerModel> CUT_MODELS = Collections.newSetFromMap(new WeakHashMap<>());

    private final boolean slim;
    private final Map<HumanoidArm, CutArm> cutArms = new EnumMap<>(HumanoidArm.class);

    GloveArmLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, boolean slim) {
        super(parent);
        this.slim = slim;
    }

    /**
     * Stands the layer on every skin type's player and mannequin renderers.
     *
     * @param event the add-layers event
     */
    public static void addToSkins(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            addTo(event.getPlayerRenderer(skin), skin);
            addTo(event.getMannequinRenderer(skin), skin);
        }
    }

    /**
     * Stands the layer on a player or mannequin renderer of one skin type, and marks the renderer's model as the
     * one whose glove arm the layer draws.
     *
     * @param renderer the avatar renderer
     * @param skin     the skin type it draws
     */
    private static void addTo(AvatarRenderer<?> renderer, PlayerModelType skin) {
        PlayerModel model = renderer.getModel();
        if (model != null) {
            CUT_MODELS.add(model);
        }
        renderer.addLayer(new GloveArmLayer(renderer, skin == PlayerModelType.SLIM));
    }

    /**
     * Hides each arm part holding a goo glove once the model is posed, on a player renderer's own model alone,
     * so the layer's cut arm draws in its place.
     *
     * @param model the posed player model
     * @param state the avatar's render state
     */
    public static void hideGlovedArms(PlayerModel model, AvatarRenderState state) {
        if (!CUT_MODELS.contains(model)) {
            return;
        }
        for (HumanoidArm side : glovedArms(state)) {
            model.getArm(side).visible = false;
        }
    }

    /**
     * Where the arm is cut, in the arm part's pixels down from its shoulder pivot: the height of the cuff's middle
     * once the glove draws through vanilla's item-in-hand offset and its third-person display transform.
     *
     * @param thirdPerson the glove's third-person display transform
     * @param leftHand    whether the glove is in the left hand
     * @param cuffModelY  the cuff's height in glove model pixels
     * @return the cut's height along the arm part, in pixels
     */
    static float cutHeight(ItemTransform thirdPerson, boolean leftHand, float cuffModelY) {
        PoseStack.Pose pose = new PoseStack.Pose();
        pose.rotate(Axis.XP.rotationDegrees(HAND_X_TURN_DEGREES));
        pose.rotate(Axis.YP.rotationDegrees(HAND_Y_TURN_DEGREES));
        float side = leftHand ? LEFT_SIDE : RIGHT_SIDE;
        pose.translate(side * HAND_OFFSET_X_PX / PIXELS_PER_BLOCK, HAND_OFFSET_Y_PX / PIXELS_PER_BLOCK,
                HAND_OFFSET_Z_PX / PIXELS_PER_BLOCK);
        thirdPerson.apply(leftHand, pose);
        Vector3f cuff = new Vector3f(MODEL_CENTER, cuffModelY / PIXELS_PER_BLOCK, MODEL_CENTER).mulPosition(pose.pose());
        return cuff.y() * PIXELS_PER_BLOCK;
    }

    /**
     * The arms holding a goo glove: the main arm for a glove in the main hand, the other arm for one in the off
     * hand.
     *
     * @param mainArm  the entity's main arm
     * @param mainHand what the main hand holds
     * @param offHand  what the off hand holds
     * @return the gloved arms, empty for none
     */
    static Set<HumanoidArm> glovedArms(HumanoidArm mainArm, ItemStack mainHand, ItemStack offHand) {
        Set<HumanoidArm> arms = EnumSet.noneOf(HumanoidArm.class);
        if (mainHand.getItem() instanceof GooGloveItem) {
            arms.add(mainArm);
        }
        if (offHand.getItem() instanceof GooGloveItem) {
            arms.add(mainArm.getOpposite());
        }
        return arms;
    }

    private static Set<HumanoidArm> glovedArms(AvatarRenderState state) {
        ItemStack offHand = state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack;
        return glovedArms(state.mainArm, state.getMainHandItemStack(), offHand);
    }

    private static boolean sleeveShown(AvatarRenderState state, HumanoidArm side) {
        return side == HumanoidArm.RIGHT ? state.showRightSleeve : state.showLeftSleeve;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state,
            float yRot, float xRot) {
        if (state.isSpectator) {
            return;
        }
        RenderType renderType = GooSubmitter.livingBodyOn(state.skin.body().texturePath(), state.isInvisible,
                state.isInvisibleToPlayer, state.appearsGlowing());
        if (renderType == null) {
            return;
        }
        BodyDraw draw = new BodyDraw(renderType, lightCoords, LivingEntityRenderer.getOverlayCoords(state,
                NO_WHITE_OVERLAY), state.isInvisible ? SEE_THROUGH_TINT : OPAQUE_TINT, state.outlineColor);
        for (HumanoidArm side : glovedArms(state)) {
            CutArm cut = cutArmFor(side, state);
            if (cut != null) {
                submitCutArm(poseStack, collector, side, cut, sleeveShown(state, side), draw);
            }
        }
    }

    /**
     * Draws one cut arm, and its sleeve where the skin shows it, in the arm part's own pose.
     *
     * @param poseStack the body's pose stack
     * @param collector the node collector
     * @param side      the gloved arm
     * @param cut       the cut arm
     * @param sleeve    whether the skin shows that arm's sleeve
     * @param draw      how the body draws this frame
     */
    private void submitCutArm(PoseStack poseStack, SubmitNodeCollector collector, HumanoidArm side, CutArm cut,
            boolean sleeve, BodyDraw draw) {
        poseStack.pushPose();
        getParentModel().root().translateAndRotate(poseStack);
        getParentModel().getArm(side).translateAndRotate(poseStack);
        draw.submit(collector, cut.arm(), poseStack);
        if (sleeve) {
            draw.submit(collector, cut.sleeve(), poseStack);
        }
        poseStack.popPose();
    }

    /**
     * The arm cut at the held glove's cuff, rebuilt only when the glove's transform moves the cut.
     *
     * @param side  the gloved arm
     * @param state the avatar's render state
     * @return the cut arm, or null where that hand's item drew nothing
     */
    private @Nullable CutArm cutArmFor(HumanoidArm side, AvatarRenderState state) {
        ItemStackRenderState held = side == HumanoidArm.RIGHT ? state.rightHandItemState : state.leftHandItemState;
        if (held.isEmpty()) {
            return null;
        }
        float cut = Math.clamp(cutHeight(held.firstLayer().itemTransform, side == HumanoidArm.LEFT, CUFF_MODEL_Y),
                SHOULDER_TOP_Y, HAND_END_Y);
        CutArm standing = cutArms.get(side);
        if (standing != null && standing.cut() == cut) {
            return standing;
        }
        CutArm built = CutArm.of(ArmBox.of(side, slim), cut);
        cutArms.put(side, built);
        return built;
    }

    /**
     * How the body around the cut arm draws this frame, so the arm draws the same.
     *
     * @param renderType   the body's render type
     * @param lightCoords  the packed light
     * @param overlayCoords the hurt-flash overlay
     * @param tint         the body's tint
     * @param outlineColor the glow outline's color, zero for none
     */
    private record BodyDraw(RenderType renderType, int lightCoords, int overlayCoords, int tint, int outlineColor) {

        void submit(SubmitNodeCollector collector, ModelPart part, PoseStack poseStack) {
            collector.submitModelPart(part, poseStack, renderType, lightCoords, overlayCoords, null, false, false,
                    tint, null, outlineColor);
        }
    }

    /**
     * Where PlayerModel.createMesh lays one arm and its sleeve on the skin texture and along x.
     *
     * @param armU    the arm's texture offset u
     * @param armV    the arm's texture offset v
     * @param sleeveU the sleeve's texture offset u
     * @param sleeveV the sleeve's texture offset v
     * @param minX    the cube's least x
     * @param width   the cube's width: 4 for a wide skin, 3 for a slim one
     */
    private record ArmBox(int armU, int armV, int sleeveU, int sleeveV, float minX, float width) {

        private static final ArmBox WIDE_RIGHT = new ArmBox(40, 16, 40, 32, -3f, 4f);
        private static final ArmBox WIDE_LEFT = new ArmBox(32, 48, 48, 48, -1f, 4f);
        private static final ArmBox SLIM_RIGHT = new ArmBox(40, 16, 40, 32, -2f, 3f);
        private static final ArmBox SLIM_LEFT = new ArmBox(32, 48, 48, 48, -1f, 3f);

        static ArmBox of(HumanoidArm side, boolean slim) {
            if (side == HumanoidArm.RIGHT) {
                return slim ? SLIM_RIGHT : WIDE_RIGHT;
            }
            return slim ? SLIM_LEFT : WIDE_LEFT;
        }
    }

    /**
     * One arm and its sleeve from the shoulder down to a cut. A cube shortened from below keeps the skin's texture
     * offsets, so its sides sample the top of the arm's texture, as the full arm's do.
     *
     * @param cut    the cut's height along the arm part, in pixels
     * @param arm    the cut arm
     * @param sleeve the cut sleeve
     */
    private record CutArm(float cut, ModelPart arm, ModelPart sleeve) {

        static CutArm of(ArmBox box, float cut) {
            return new CutArm(cut, cutCube(box.armU(), box.armV(), box, cut, 0f),
                    cutCube(box.sleeveU(), box.sleeveV(), box, cut, SLEEVE_GROWTH));
        }

        private static ModelPart cutCube(int texU, int texV, ArmBox box, float cut, float growth) {
            ModelPart.Cube cube = new ModelPart.Cube(texU, texV, box.minX(), SHOULDER_TOP_Y, ARM_MIN_Z, box.width(),
                    cut - SHOULDER_TOP_Y, ARM_DEPTH, growth, growth, growth, false, SKIN_TEXTURE_SIZE,
                    SKIN_TEXTURE_SIZE, EnumSet.allOf(Direction.class));
            return new ModelPart(List.of(cube), Map.of());
        }
    }
}

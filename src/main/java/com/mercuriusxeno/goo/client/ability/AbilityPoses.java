package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.network.ArmPosePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * The poses abilities hold the glove arm in on this client, in place of the
 * swing: each player's pose with the game time it began, laid over the
 * arm vanilla posed on the player's model, and over the glove hand in
 * first person. A slinging charge draws the glove hand across to the
 * opposite shoulder while it is held, then flings the arm out to the glove
 * side on release and lets it fall back.
 * decision shards-sling-then-morph-to-flechettes
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AbilityPoses {

    /** Ticks the wind-up takes to draw the hand to the shoulder. */
    static final float WIND_UP_TICKS = 4f;
    /** Ticks the fling takes to sweep the arm out. */
    static final float FLING_OUT_TICKS = 3f;
    /** Ticks the arm holds flung out. */
    static final float FLING_HOLD_TICKS = 3f;
    /** Ticks the arm takes to fall back to vanilla's pose after the fling. */
    static final float FLING_RETURN_TICKS = 5f;
    /** The arm drawn across the chest, the hand at the opposite shoulder: raised forward and turned inward. */
    static final Angles WOUND = new Angles(-1.75f, -1.0f);
    /** The arm flung out to the glove side, a little above level. */
    static final Angles FLUNG = new Angles(-1.65f, 0.95f);
    /** The first-person hand's shift toward the opposite shoulder while wound up, in screen blocks. */
    private static final float HAND_ACROSS = 0.42f;
    private static final float HAND_UP = 0.12f;
    private static final float HAND_IN_DEGREES = 38f;
    private static final float HAND_TILT_DEGREES = 18f;
    /** The first-person hand's shift out to the glove side while flung. */
    private static final float HAND_OUT = 0.18f;
    private static final float HAND_OUT_DEGREES = -30f;

    /** The left hand's turn runs opposite the right's. */
    private static final float MIRRORED = -1f;
    /** The smoothstep's terms: three times the square less twice the cube. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private static final Map<Integer, Posed> POSES = new HashMap<>();

    private AbilityPoses() {
    }

    /**
     * An arm's rotation about the shoulder, for a glove in the right hand;
     * the left hand mirrors the turn.
     *
     * @param xRot the raise, negative forward
     * @param yRot the turn, negative across the chest
     */
    record Angles(float xRot, float yRot) {

        Angles lerp(float share, Angles to) {
            return new Angles(Mth.lerp(share, xRot, to.xRot), Mth.lerp(share, yRot, to.yRot));
        }
    }

    /**
     * Where a pose holds the arm at a moment, and how fully it overrides
     * vanilla's pose.
     *
     * @param angles the arm's rotation, for a right-hand glove
     * @param weight the share of the pose laid over vanilla's, 0 to 1
     */
    record Held(Angles angles, float weight) {
    }

    /**
     * One player's pose.
     *
     * @param kind       the pose
     * @param since      the game time it began
     * @param gloveRight whether the glove is in the right hand
     */
    record Posed(ArmPoseKind kind, double since, boolean gloveRight) {
    }

    /**
     * Where a pose holds the arm an age into it: the wind-up draws the hand
     * to the shoulder and holds it there; the fling sweeps from the shoulder
     * out, holds, and falls back, after which it is over.
     *
     * @param kind the pose
     * @param age  ticks since it began
     * @return the arm's hold, at weight 0 once the pose is over
     */
    static Held heldAt(ArmPoseKind kind, double age) {
        return switch (kind) {
            case WIND_UP -> new Held(WOUND, smooth((float) age / WIND_UP_TICKS));
            case FLING -> flingAt((float) age);
            case NONE -> new Held(WOUND, 0f);
        };
    }

    private static Held flingAt(float age) {
        if (age < FLING_OUT_TICKS) {
            return new Held(WOUND.lerp(smooth(age / FLING_OUT_TICKS), FLUNG), 1f);
        }
        float holding = age - FLING_OUT_TICKS - FLING_HOLD_TICKS;
        return new Held(FLUNG, 1f - smooth(holding / FLING_RETURN_TICKS));
    }

    /**
     * Whether a pose is over an age into it, so the store drops it.
     *
     * @param kind the pose
     * @param age  ticks since it began
     * @return true for a fling that has fallen back, or no pose
     */
    static boolean overAt(ArmPoseKind kind, double age) {
        return kind == ArmPoseKind.NONE
                || kind == ArmPoseKind.FLING && age >= FLING_OUT_TICKS + FLING_HOLD_TICKS + FLING_RETURN_TICKS;
    }

    private static float smooth(float share) {
        float clamped = Math.clamp(share, 0f, 1f);
        return clamped * clamped * (SMOOTH_SQUARE - SMOOTH_CUBE * clamped);
    }

    /**
     * Sets a player's pose from now; no pose clears it.
     *
     * @param entityId   the player's entity id
     * @param kind       the pose
     * @param gloveRight whether the glove is in the right hand
     */
    public static void pose(int entityId, ArmPoseKind kind, boolean gloveRight) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == ArmPoseKind.NONE || mc.level == null) {
            POSES.remove(entityId);
            return;
        }
        Posed held = POSES.get(entityId);
        if (held == null || held.kind() != kind) {
            POSES.put(entityId, new Posed(kind, mc.level.getGameTime(), gloveRight));
        }
    }

    /**
     * The pose a player holds now.
     *
     * @param entityId the player's entity id
     * @return the pose, or null for none
     */
    static Posed posedAt(int entityId) {
        return POSES.get(entityId);
    }

    /**
     * Handles a pose the server sent for a player.
     *
     * @param payload the pose payload
     * @param context the network context
     */
    public static void onPayload(ArmPosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> pose(payload.entityId(), payload.kind(), payload.gloveRight()));
    }

    /** Drops every pose, as a disconnect does. */
    public static void clear() {
        POSES.clear();
    }

    /**
     * Lays a player's pose over the glove arm vanilla posed on the model,
     * once vanilla's setup is done.
     *
     * @param model    the player's model
     * @param entityId the player's entity id
     */
    public static void layOver(HumanoidModel<?> model, int entityId) {
        Posed posed = POSES.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (posed == null || mc.level == null) {
            return;
        }
        double age = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) - posed.since();
        Held held = heldAt(posed.kind(), age);
        ModelPart arm = posed.gloveRight() ? model.rightArm : model.leftArm;
        float mirror = posed.gloveRight() ? 1f : MIRRORED;
        arm.xRot = Mth.lerp(held.weight(), arm.xRot, held.angles().xRot());
        arm.yRot = Mth.lerp(held.weight(), arm.yRot, held.angles().yRot() * mirror);
        arm.zRot = Mth.lerp(held.weight(), arm.zRot, 0f);
    }

    /**
     * Moves the local player's glove hand in first person: across toward
     * the opposite shoulder while wound up, out to the glove side as it
     * flings; and drops the poses that are over.
     *
     * @param event the hand render event
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + event.getPartialTick();
        POSES.entrySet().removeIf(entry -> overAt(entry.getValue().kind(), now - entry.getValue().since()));
        Posed posed = POSES.get(player.getId());
        if (posed != null && armOf(player, event.getHand()) == gloveArm(posed)) {
            moveHand(event.getPoseStack(), posed, now - posed.since());
        }
    }

    /**
     * Moves the first-person glove hand by its pose an age into it.
     *
     * @param pose  the hand's pose stack
     * @param posed the pose
     * @param age   ticks since it began
     */
    private static void moveHand(PoseStack pose, Posed posed, double age) {
        Held held = heldAt(posed.kind(), age);
        float side = posed.gloveRight() ? 1f : MIRRORED;
        boolean flinging = posed.kind() == ArmPoseKind.FLING;
        float out = flinging ? held.weight() * outShare(age) : 0f;
        float wound = flinging ? held.weight() - out : held.weight();
        pose.translate(side * (-HAND_ACROSS * wound + HAND_OUT * out), HAND_UP * wound, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(side * (HAND_IN_DEGREES * wound + HAND_OUT_DEGREES * out)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * HAND_TILT_DEGREES * wound));
    }

    private static HumanoidArm gloveArm(Posed posed) {
        return posed.gloveRight() ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    private static float outShare(double age) {
        return smooth((float) age / FLING_OUT_TICKS);
    }

    private static HumanoidArm armOf(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }
}

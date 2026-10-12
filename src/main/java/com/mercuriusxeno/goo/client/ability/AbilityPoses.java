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
 * first person. A slinging charge eases the glove hand left across the
 * chest while it is held, subtly and never up to the shoulder, reaching its
 * furthest at full charge, then backhands the arm out to the glove side
 * from wherever it got to on release and lets it fall back. A held-out arm
 * points along the player's aim, a thrown one flings forward from there,
 * and a raised one holds the hand up overhead; a held pose let go with no
 * release of its own eases back down.
 * decision shards-sling-then-morph-to-flechettes
 * decision ability-json-names-its-arm-pose
 * decision use-key-throw-swings-no-more
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AbilityPoses {

    /** Ticks the wind-up takes to ease the hand across the chest: Shards' full charge. */
    static final float WIND_UP_TICKS = 30f;
    /** Ticks the fling takes to sweep the arm out. */
    static final float FLING_OUT_TICKS = 3f;
    /** Ticks the arm holds flung out. */
    static final float FLING_HOLD_TICKS = 3f;
    /** Ticks the arm takes to fall back to vanilla's pose after the fling. */
    static final float FLING_RETURN_TICKS = 5f;
    /** The arm eased across the chest, the hand at chest height before the body: raised a little and turned in. */
    static final Angles WOUND = new Angles(-1.05f, -0.55f);
    /** The arm flung out to the glove side, a little above level. */
    static final Angles FLUNG = new Angles(-1.65f, 0.95f);
    /** Ticks a held-out or raised arm takes to ease into its hold. */
    static final float HOLD_EASE_TICKS = 4f;
    /** Ticks a held pose let go takes to ease back to vanilla's. */
    static final float LOWER_TICKS = 5f;
    /** Ticks a throw takes to fling the arm forward and down. */
    static final float THROW_OUT_TICKS = 3f;
    /** Ticks the arm holds its follow-through. */
    static final float THROW_HOLD_TICKS = 2f;
    /** The arm held level before the body, as a drawn bow holds it; the head's aim is added on top. */
    static final Angles AIMED = new Angles(-Mth.HALF_PI, -0.1f);
    /** The arm raised overhead, the hand up. */
    static final Angles RAISED = new Angles(-2.85f, 0f);
    /** A throw's follow-through: forward and down, a little across the body. */
    static final Angles FOLLOW_THROUGH = new Angles(-0.55f, -0.35f);
    /** How far into a pose its steady hold is read, past any ease. */
    private static final double STEADY_AGE = 1000;
    /** The first-person hand's shift left across the chest while wound up, in screen blocks, and no higher. */
    private static final float HAND_ACROSS = 0.26f;
    private static final float HAND_IN_DEGREES = 20f;
    private static final float HAND_TILT_DEGREES = 6f;
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
     * @param angles    the arm's rotation, for a right-hand glove
     * @param weight    the share of the pose laid over vanilla's, 0 to 1
     * @param headShare the share of the head's aim added to the angles, 1 for an arm pointing where the player looks
     */
    record Held(Angles angles, float weight, float headShare) {

        Held(Angles angles, float weight) {
            this(angles, weight, 0f);
        }
    }

    /**
     * One player's pose.
     *
     * @param kind       the pose
     * @param since      the game time it began
     * @param gloveRight whether the glove is in the right hand
     * @param from       how far the pose before it had eased, 0 to 1; this one starts there
     * @param lowering   whether a held pose was let go and is easing back down
     */
    record Posed(ArmPoseKind kind, double since, boolean gloveRight, float from, boolean lowering) {

        Posed(ArmPoseKind kind, double since, boolean gloveRight, float from) {
            this(kind, since, gloveRight, from, false);
        }

        /**
         * Whether the arm holds a frame now, rather than easing down from it.
         *
         * @param frame the frame
         * @return true while this pose holds that frame
         */
        boolean holding(ArmPoseKind frame) {
            return kind == frame && !lowering;
        }
    }

    /**
     * Where a pose holds the arm an age into it: the wind-up eases the hand
     * across the chest over the charge and holds it there; the fling
     * backhands from where the wind-up got to out, holds, and falls back,
     * after which it is over. A held-out arm eases level along the aim and a
     * raised one overhead, each holding there; a throw flings forward and
     * down from the aim, holds its follow-through, and falls back.
     *
     * @param kind the pose
     * @param age  ticks since it began
     * @param from how far the pose before it had eased, 0 to 1
     * @return the arm's hold, at weight 0 once the pose is over
     */
    static Held heldAt(ArmPoseKind kind, double age, float from) {
        return kind.holds() ? holdAt(kind, (float) age, from) : playedAt(kind, (float) age, from);
    }

    private static Held holdAt(ArmPoseKind kind, float age, float from) {
        return switch (kind) {
            case HOLD_OUT -> new Held(AIMED, easedIn(age / HOLD_EASE_TICKS, from), 1f);
            case HOLD_UP -> new Held(RAISED, easedIn(age / HOLD_EASE_TICKS, from));
            default -> new Held(WOUND, easedIn(age / WIND_UP_TICKS, from));
        };
    }

    private static Held playedAt(ArmPoseKind kind, float age, float from) {
        return switch (kind) {
            case FLING -> flingAt(age, from);
            case THROW -> throwAt(age, from);
            default -> new Held(WOUND, 0f);
        };
    }

    /**
     * Where a player's pose holds the arm an age into it, a held pose let go
     * easing back down from where it had got to.
     *
     * @param posed the pose
     * @param age   ticks since it began
     * @return the arm's hold
     */
    static Held heldAt(Posed posed, double age) {
        if (!posed.lowering()) {
            return heldAt(posed.kind(), age, posed.from());
        }
        Held steady = heldAt(posed.kind(), STEADY_AGE, 1f);
        float remaining = posed.from() * (1f - smooth((float) age / LOWER_TICKS));
        return new Held(steady.angles(), remaining, steady.headShare());
    }

    private static float easedIn(float share, float from) {
        return Mth.lerp(smooth(share), from, 1f);
    }

    private static Held flingAt(float age, float from) {
        if (age < FLING_OUT_TICKS) {
            float out = smooth(age / FLING_OUT_TICKS);
            return new Held(WOUND.lerp(out, FLUNG), Mth.lerp(out, from, 1f));
        }
        float holding = age - FLING_OUT_TICKS - FLING_HOLD_TICKS;
        return new Held(FLUNG, 1f - smooth(holding / FLING_RETURN_TICKS));
    }

    private static Held throwAt(float age, float from) {
        if (age < THROW_OUT_TICKS) {
            float out = smooth(age / THROW_OUT_TICKS);
            return new Held(AIMED.lerp(out, FOLLOW_THROUGH), Mth.lerp(out, from, 1f), 1f - out);
        }
        float holding = age - THROW_OUT_TICKS - THROW_HOLD_TICKS;
        return new Held(FOLLOW_THROUGH, 1f - smooth(holding / FLING_RETURN_TICKS));
    }

    /**
     * Whether a pose is over an age into it, so the store drops it.
     *
     * @param posed the pose
     * @param age   ticks since it began
     * @return true for a fling or a throw that has fallen back, a held pose
     *         eased down, or no pose
     */
    static boolean overAt(Posed posed, double age) {
        if (posed.lowering()) {
            return age >= LOWER_TICKS;
        }
        return switch (posed.kind()) {
            case NONE -> true;
            case FLING -> age >= FLING_OUT_TICKS + FLING_HOLD_TICKS + FLING_RETURN_TICKS;
            case THROW -> age >= THROW_OUT_TICKS + THROW_HOLD_TICKS + FLING_RETURN_TICKS;
            case WIND_UP, HOLD_OUT, HOLD_UP -> false;
        };
    }

    private static float smooth(float share) {
        float clamped = Math.clamp(share, 0f, 1f);
        return clamped * clamped * (SMOOTH_SQUARE - SMOOTH_CUBE * clamped);
    }

    /**
     * Sets a player's pose from now. No pose lets a held pose ease back
     * down and leaves a fling or a throw to finish.
     *
     * @param entityId   the player's entity id
     * @param kind       the pose
     * @param gloveRight whether the glove is in the right hand
     */
    public static void pose(int entityId, ArmPoseKind kind, boolean gloveRight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            POSES.remove(entityId);
            return;
        }
        long now = mc.level.getGameTime();
        Posed next = posedNext(POSES.get(entityId), kind, gloveRight, now);
        if (next == null) {
            POSES.remove(entityId);
        } else {
            POSES.put(entityId, next);
        }
    }

    /**
     * The pose a player takes when told a frame: a held pose let go eases
     * down from where it got to, a fling or a throw told nothing plays on,
     * the same frame still held carries on, and any other frame starts now
     * from wherever the arm had eased.
     *
     * @param held       the pose held until now, or null
     * @param kind       the frame told
     * @param gloveRight whether the glove is in the right hand
     * @param now        the game time
     * @return the pose to hold, or null for none
     */
    static Posed posedNext(Posed held, ArmPoseKind kind, boolean gloveRight, double now) {
        Posed live = held == null || overAt(held, now - held.since()) ? null : held;
        if (kind == ArmPoseKind.NONE) {
            return letGo(live, now);
        }
        if (live != null && live.holding(kind)) {
            return live;
        }
        return new Posed(kind, now, gloveRight, easedFrom(live, now));
    }

    /**
     * A pose let go: a held pose eases down from where it got to, and
     * anything else plays on.
     *
     * @param live the pose still playing, or null
     * @param now  the game time
     * @return the pose to hold, or null for none
     */
    private static Posed letGo(Posed live, double now) {
        if (live == null || live.lowering() || !live.kind().holds()) {
            return live;
        }
        return new Posed(live.kind(), now, live.gloveRight(), easedFrom(live, now), true);
    }

    /**
     * How far a pose had eased the arm by a moment, where a pose taking over
     * from it starts.
     *
     * @param held the pose held until now, or null
     * @param now  the game time
     * @return its weight then, 0 for none
     */
    static float easedFrom(Posed held, double now) {
        return held == null ? 0f : heldAt(held, now - held.since()).weight();
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
        Held held = heldAt(posed, age);
        ModelPart arm = posed.gloveRight() ? model.rightArm : model.leftArm;
        float mirror = posed.gloveRight() ? 1f : MIRRORED;
        float xRot = held.angles().xRot() + model.head.xRot * held.headShare();
        float yRot = held.angles().yRot() * mirror + model.head.yRot * held.headShare();
        arm.xRot = Mth.lerp(held.weight(), arm.xRot, xRot);
        arm.yRot = Mth.lerp(held.weight(), arm.yRot, yRot);
        arm.zRot = Mth.lerp(held.weight(), arm.zRot, 0f);
    }

    /**
     * Moves the local player's glove hand in first person: left across the
     * chest while wound up, with the goo swelling in it, out to the glove
     * side as it flings; and drops the poses that are over.
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
        POSES.entrySet().removeIf(entry -> overAt(entry.getValue(), now - entry.getValue().since()));
        Posed posed = POSES.get(player.getId());
        if (posed != null && movesTheHand(posed) && armOf(player, event.getHand()) == gloveArm(posed)) {
            moveGloveHand(event, posed, now);
        }
    }

    private static void moveGloveHand(RenderHandEvent event, Posed posed, double now) {
        moveHand(event.getPoseStack(), posed, now - posed.since());
        if (posed.holding(ArmPoseKind.WIND_UP)) {
            ShardsCharge.drawHandBlob(event, posed.gloveRight(), now);
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
        Held held = heldAt(posed, age);
        float side = posed.gloveRight() ? 1f : MIRRORED;
        boolean flinging = posed.kind() == ArmPoseKind.FLING;
        float out = flinging ? held.weight() * outShare(age) : 0f;
        float wound = flinging ? held.weight() - out : held.weight();
        pose.translate(side * (-HAND_ACROSS * wound + HAND_OUT * out), 0f, 0f);
        pose.mulPose(Axis.YP.rotationDegrees(side * (HAND_IN_DEGREES * wound + HAND_OUT_DEGREES * out)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * HAND_TILT_DEGREES * wound));
    }

    /**
     * Whether a pose moves the first-person hand: the wind-up and the fling
     * do, and the poses held along the aim or overhead read on the model alone.
     *
     * @param posed the pose
     * @return true for a wind-up or a fling
     */
    private static boolean movesTheHand(Posed posed) {
        return posed.kind() == ArmPoseKind.WIND_UP || posed.kind() == ArmPoseKind.FLING;
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

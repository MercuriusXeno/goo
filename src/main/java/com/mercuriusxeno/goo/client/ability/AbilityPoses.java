package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.item.GooCasters;
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
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The poses abilities hold the glove arm in on this client, in place of the
 * swing: each player's pose with the game time it began, laid over the
 * arm vanilla posed on the player's model, and over the glove hand in
 * first person. A held-out arm points along the player's aim, and a raised
 * one holds the hand up at face height; a held pose let go eases back down.
 * A blob thrown takes vanilla's swing, which is no pose of this class. Every
 * pose moves the first-person hand too: held out forward toward the
 * crosshair, and held up lifted a little. In third person, while a blob
 * stands lifted, every pose gives way to one hold, the hand out before the
 * body, palm up under the blob.
 * decision ability-json-names-its-arm-pose
 * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up
 * decision use-key-throw-swings-no-more
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AbilityPoses {

    /** The angles a frame holding no pose lays over the arm, at no weight. */
    static final Angles UNPOSED = new Angles(0f, 0f);
    /** Ticks a held-out or raised arm takes to ease into its hold. */
    static final float HOLD_EASE_TICKS = 4f;
    /** Ticks a held pose let go takes to ease back to vanilla's. */
    static final float LOWER_TICKS = 5f;
    /** The arm held level before the body, as a drawn bow holds it; the head's aim is added on top. */
    static final Angles AIMED = new Angles(-Mth.HALF_PI, -0.1f);
    /** The arm raised forward to face height, the hand up before the face and a little in. */
    static final Angles RAISED = new Angles(-2.1f, -0.15f);
    /** How far into a pose its steady hold is read, past any ease. */
    private static final double STEADY_AGE = 1000;
    /** The first-person hand pushed forward and in toward the crosshair while held out. */
    static final HandOffset HELD_OUT_HAND = new HandOffset(-0.1f, 0.04f, 0.22f, 0f, 0f);
    /** The first-person hand lifted a little and forward while raised, its wrist kept inside the screen. */
    static final HandOffset RAISED_HAND = new HandOffset(0f, 0.1f, 0.08f, 0f, 0f);
    /** Vanilla's place for the hand. */
    static final HandOffset VANILLA_HAND = new HandOffset(0f, 0f, 0f, 0f, 0f);
    /**
     * The glove hand held out before the body under a lifted blob: the arm a
     * little below level, which turns the glove's palm up, and in across the
     * chest toward the body's middle. The arm is one part with no elbow, so
     * the palm faces straight up only with the arm level; this tilt leaves it
     * a little short of that.
     * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up
     */
    static final Angles HAND_OUT = new Angles(-1.37f, -0.35f);

    /** The left hand's turn runs opposite the right's. */
    private static final float MIRRORED = -1f;
    /** The smoothstep's terms: three times the square less twice the cube. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private static final Map<Integer, Posed> POSES = new HashMap<>();
    /** The game time each caster's glove arm last swung under a lifted blob. */
    private static final Map<Integer, Double> SWUNG = new HashMap<>();

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
    }

    /**
     * Where a pose moves the first-person glove hand from vanilla's place,
     * for a glove in the right hand; the left hand mirrors the shift across
     * and both turns.
     *
     * @param across       the shift right, in screen blocks, negative toward the crosshair and across the chest
     * @param lift         the shift up, in screen blocks
     * @param forward      the shift away from the camera, in screen blocks
     * @param turnDegrees  the turn about the vertical axis
     * @param tiltDegrees  the roll about the view axis
     */
    record HandOffset(float across, float lift, float forward, float turnDegrees, float tiltDegrees) {

        HandOffset lerp(float share, HandOffset to) {
            return new HandOffset(Mth.lerp(share, across, to.across), Mth.lerp(share, lift, to.lift),
                    Mth.lerp(share, forward, to.forward), Mth.lerp(share, turnDegrees, to.turnDegrees),
                    Mth.lerp(share, tiltDegrees, to.tiltDegrees));
        }

        HandOffset scaled(float weight) {
            return VANILLA_HAND.lerp(weight, this);
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
     * Where a pose holds the arm an age into it: a held-out arm eases level
     * along the aim and a raised one to face height, each holding there.
     *
     * @param kind the pose
     * @param age  ticks since it began
     * @param from how far the pose before it had eased, 0 to 1
     * @return the arm's hold, at weight 0 for no pose
     */
    static Held heldAt(ArmPoseKind kind, double age, float from) {
        return switch (kind) {
            case HOLD_OUT -> new Held(AIMED, easedIn((float) age / HOLD_EASE_TICKS, from), 1f);
            case HOLD_UP -> new Held(RAISED, easedIn((float) age / HOLD_EASE_TICKS, from));
            case NONE -> new Held(UNPOSED, 0f);
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

    /**
     * Whether a pose is over an age into it, so the store drops it.
     *
     * @param posed the pose
     * @param age   ticks since it began
     * @return true for a held pose eased down, or no pose
     */
    static boolean overAt(Posed posed, double age) {
        if (posed.lowering()) {
            return age >= LOWER_TICKS;
        }
        return switch (posed.kind()) {
            case NONE -> true;
            case HOLD_OUT, HOLD_UP -> false;
        };
    }

    private static float smooth(float share) {
        float clamped = Math.clamp(share, 0f, 1f);
        return clamped * clamped * (SMOOTH_SQUARE - SMOOTH_CUBE * clamped);
    }

    /**
     * Sets a player's pose from now. No pose lets a held pose ease back
     * down.
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
     * down from where it got to, the same frame still held carries on, and
     * any other frame starts now
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
        SWUNG.clear();
    }

    /**
     * Lays a player's pose over the glove arm vanilla posed on the model,
     * once vanilla's setup is done, and while the player's blob stands lifted
     * the hand-out hold over that, unless a thrown blob's swing takes the arm.
     * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up
     *
     * @param model    the player's model
     * @param entityId the player's entity id
     * @param swinging the arm vanilla swings this frame, or null for none
     */
    public static void layOver(HumanoidModel<?> model, int entityId, @Nullable HumanoidArm swinging) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Posed posed = POSES.get(entityId);
        gloveArmOf(mc, entityId, posed).ifPresent(side -> layOverArm(model, side, posed, now,
                handOutOf(entityId, now, swinging == side)));
    }

    private static void layOverArm(HumanoidModel<?> model, HumanoidArm side, @Nullable Posed posed, double now,
                                   float handOut) {
        if (posed == null && handOut <= 0f) {
            return;
        }
        ModelPart arm = model.getArm(side);
        double age = posed == null ? 0 : now - posed.since();
        ArmTurn turned = turnedArm(new ArmTurn(arm.xRot, arm.yRot, arm.zRot), posed, age,
                new ArmTurn(model.head.xRot, model.head.yRot, 0f), handOut, side == HumanoidArm.RIGHT);
        arm.xRot = turned.xRot();
        arm.yRot = turned.yRot();
        arm.zRot = turned.zRot();
    }

    /**
     * The arm holding a player's glove: the one its pose names, or the one
     * its caster is held in.
     *
     * @param mc       the client
     * @param entityId the player's entity id
     * @param posed    the player's pose, or null for none
     * @return the glove arm, empty where the entity is no player
     */
    private static Optional<HumanoidArm> gloveArmOf(Minecraft mc, int entityId, @Nullable Posed posed) {
        if (posed != null) {
            return Optional.of(gloveArm(posed));
        }
        return mc.level.getEntity(entityId) instanceof Player player
                ? Optional.of(GooCasters.casterOnTheRight(player) ? HumanoidArm.RIGHT : HumanoidArm.LEFT)
                : Optional.empty();
    }

    /**
     * An arm part's turn about its shoulder, as the model stores it.
     *
     * @param xRot the pitch, negative forward
     * @param yRot the yaw, negative across the chest for a right arm
     * @param zRot the roll, outward from the side
     */
    record ArmTurn(float xRot, float yRot, float zRot) {
    }

    /**
     * The glove arm's turn: vanilla's, the named pose laid over it, and while
     * a blob stands lifted the hand-out hold laid over that, whatever pose the
     * ability names.
     * decision ability-json-names-its-arm-pose
     * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up
     *
     * @param vanilla    the turn vanilla posed
     * @param posed      the named pose, or null for none
     * @param age        ticks since the named pose began
     * @param head       the head's turn, which a held-out arm follows
     * @param handOut    the share of the hand-out hold, 0 to 1
     * @param gloveRight whether the glove is in the right hand
     * @return the arm's turn
     */
    static ArmTurn turnedArm(ArmTurn vanilla, @Nullable Posed posed, double age, ArmTurn head, float handOut,
                             boolean gloveRight) {
        float mirror = gloveRight ? 1f : MIRRORED;
        ArmTurn named = vanilla;
        if (posed != null) {
            Held held = heldAt(posed, age);
            named = new ArmTurn(Mth.lerp(held.weight(), vanilla.xRot(), held.angles().xRot()
                    + head.xRot() * held.headShare()),
                    Mth.lerp(held.weight(), vanilla.yRot(), held.angles().yRot() * mirror
                            + head.yRot() * held.headShare()),
                    Mth.lerp(held.weight(), vanilla.zRot(), 0f));
        }
        return new ArmTurn(Mth.lerp(handOut, named.xRot(), HAND_OUT.xRot()),
                Mth.lerp(handOut, named.yRot(), HAND_OUT.yRot() * mirror), Mth.lerp(handOut, named.zRot(), 0f));
    }

    /**
     * The share of the hand-out hold a caster's arm takes now: the lifted
     * blob's, given way to a thrown blob's swing. Remembers when the glove
     * arm last swung under a lift, and forgets it once no lift stands.
     *
     * @param entityId    the caster's entity id
     * @param now         the game time, with the frame's partial tick
     * @param gloveSwings whether vanilla swings the glove arm this frame
     * @return the share, 0 to 1
     */
    private static float handOutOf(int entityId, double now, boolean gloveSwings) {
        float lifted = LiftedPalmBlob.handOutAt(entityId, now);
        if (lifted <= 0f) {
            SWUNG.remove(entityId);
            return 0f;
        }
        Double swungAt = SWUNG.get(entityId);
        if (gloveSwings) {
            SWUNG.put(entityId, now);
        }
        return yieldedToTheSwing(lifted, gloveSwings, swungAt, LiftedPalmBlob.running(entityId, (long) now), now);
    }

    /**
     * The share of the hand-out hold a lift lays over the arm: easing in as
     * the blob hops off the palm, whole while the cast runs, and easing down
     * once it ends, as the held frames ease. A blob turned round off the palm
     * finds the hand already out.
     * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up
     *
     * @param sinceLift  ticks since the lift began
     * @param sinceEnded ticks since the cast ended, negative while it runs
     * @param hopped     whether the lift began on the palm, with a hop
     * @return the share, 0 to 1
     */
    static float handOutShare(double sinceLift, double sinceEnded, boolean hopped) {
        float in = hopped ? smooth((float) sinceLift / HOLD_EASE_TICKS) : 1f;
        float out = sinceEnded < 0 ? 1f : 1f - smooth((float) sinceEnded / LOWER_TICKS);
        return in * out;
    }

    /**
     * The hand-out hold given way to a thrown blob's swing: none while the
     * glove arm swings, none after it once the cast is over, and easing back
     * in after it while the cast still runs, as Dart's next javelin forms.
     * decision third-person-arm-holds-the-hand-out-while-a-blob-is-up: a thrown blob keeps its swing on release
     *
     * @param handOut     the lift's share of the hold
     * @param swinging    whether vanilla swings the glove arm now
     * @param swungAt     the game time the glove arm last swung under this lift, or null for no swing
     * @param castRunning whether the cast still runs
     * @param now         the game time
     * @return the share, 0 to 1
     */
    static float yieldedToTheSwing(float handOut, boolean swinging, @Nullable Double swungAt, boolean castRunning,
                                   double now) {
        if (swinging) {
            return 0f;
        }
        if (swungAt == null) {
            return handOut;
        }
        return castRunning ? handOut * smooth((float) (now - swungAt) / HOLD_EASE_TICKS) : 0f;
    }

    /**
     * Moves the local player's glove hand in first person by whichever pose
     * it holds: forward toward the crosshair while held out; up a little
     * while raised. Drops the poses
     * that are over.
     * decision ability-json-names-its-arm-pose
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
        if (posed != null && armOf(player, event.getHand()) == gloveArm(posed)) {
            moveGloveHand(event, posed, now);
        }
    }

    private static void moveGloveHand(RenderHandEvent event, Posed posed, double now) {
        moveHand(event.getPoseStack(), handAt(posed, now - posed.since()), posed.gloveRight());
    }

    private static void moveHand(PoseStack pose, HandOffset hand, boolean gloveRight) {
        float side = gloveRight ? 1f : MIRRORED;
        pose.translate(side * hand.across(), hand.lift(), -hand.forward());
        pose.mulPose(Axis.YP.rotationDegrees(side * hand.turnDegrees()));
        pose.mulPose(Axis.ZP.rotationDegrees(side * hand.tiltDegrees()));
    }

    /**
     * Where a pose moves the first-person glove hand an age into it, eased
     * by the same weight that lays the pose over the arm on the model.
     *
     * @param posed the pose
     * @param age   ticks since it began
     * @return the hand's offset, for a right-hand glove; vanilla's place once the pose is over
     */
    static HandOffset handAt(Posed posed, double age) {
        HandOffset furthest = switch (posed.kind()) {
            case NONE -> VANILLA_HAND;
            case HOLD_OUT -> HELD_OUT_HAND;
            case HOLD_UP -> RAISED_HAND;
        };
        return furthest.scaled(heldAt(posed, age).weight());
    }

    private static HumanoidArm gloveArm(Posed posed) {
        return posed.gloveRight() ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
    }

    private static HumanoidArm armOf(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }
}

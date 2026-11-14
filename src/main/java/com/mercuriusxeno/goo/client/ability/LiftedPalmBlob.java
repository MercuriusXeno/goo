package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GloveInputGate;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mercuriusxeno.goo.network.PalmBlobLiftPayload;
import com.mercuriusxeno.goo.network.StreamHolds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The palm blob lifted off the glove while its caster holds any ability: as
 * a stream or a channel runs, or a release ability charges with right click
 * held, the blob hops off the palm to its rest point (BlobRest): the
 * steady point by the glove, or the center line below the crosshair for a
 * centered ability, shrinking from the pancake spread over the palm into a
 * smaller cube as it goes, and stands there in the goo type the glove has selected, beating
 * between two sizes below the pancake's while the cast is held. Once the
 * held ticks stop for longer than a stream's gap it comes back down and
 * smears across the palm into the pancake. The glove's palm draws none
 * while the blob is off it, so the caster shows one blob, and each Goop or
 * Slick drip leaves the blob where it stands. The local player's lift reads
 * the glove press; another caster's reads the held ticks the server passes
 * on. The local first-person player's blob draws in the hand's pass, over
 * the glove and turned with it; every other caster's draws in the level,
 * turned with that caster's look. Knives' charge floats the blob instead
 * ahead of and above the glove hand through the wind-up and the backhand's
 * sweep, facing the look in both passes, at the palm blob's rest size for
 * the charge to swell.
 * palm-blob-lifts-as-the-spray-source
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 * every-ability-casts-from-the-lifted-palm-blob
 * centered-abilities-float-the-blob-below-the-crosshair
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LiftedPalmBlob {

    /**
     * The most game ticks a stream tick may land after the last and still
     * keep the spray running: the gap a server hold continues across.
     */
    public static final int STREAM_GAP_TICKS = StreamHolds.LATEST_CONTINUING_GAP;
    /** The ticks one beat of the lifted blob's swell takes. */
    static final double BEAT_TICKS = 12;
    /**
     * The lifted cube's least and greatest size against the palm blob's rest
     * size, both below it, so the pancake shrinks into a smaller cube as it lifts.
     * operator ruling 2026-11-13: the pancake can be spread out and then it shrinks in cuboid form
     */
    static final float LIFTED_LEAST = 0.75f;
    static final float LIFTED_GREATEST = 0.9f;
    /**
     * A flying blob's size the palm blob at rest shows as in the world: the
     * first-person glove draws its model at half size, so its rest blob reads
     * half a flying blob's width.
     */
    static final float FLIGHT_SIZE_OF_PALM_REST = 0.5f;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;

    /** Each lifting caster's lift, by entity id. */
    private static final Map<Integer, Lift> LIFTS = new ConcurrentHashMap<>();

    private LiftedPalmBlob() {
    }

    /**
     * A caster's blob off the palm, a pure function of the time: from the
     * palm it hops, rises to the source and stands there while the spray
     * runs, and once the spray ends it settles back onto the palm. A spray
     * begun again while the blob is off the palm turns it round from the pose
     * it had reached, with no hop.
     * every-lift-begins-with-a-hop
     *
     * @param began          the game time the spray began
     * @param from           the pose the blob stood in as the spray began
     * @param hops           whether the lift began on the palm, so it hops
     * @param lastStreamTick the game time of the caster's last stream tick that threw drips
     * @param sentGlove      the glove the server sent with that tick
     * @param floats         whether Knives' charge holds it, floating it ahead of the hand at its rest size
     */
    record Lift(long began, PalmBlobHop.Pose from, boolean hops, long lastStreamTick, Vec3 sentGlove,
                boolean floats) {

        /**
         * A lift from the palm as a spray begins.
         *
         * @param gameTime  the game time of the spray's first stream tick
         * @param sentGlove the glove the server sent with that tick
         * @return the lift
         */
        static Lift beginning(long gameTime, Vec3 sentGlove) {
            return new Lift(gameTime, PalmBlobHop.Pose.ON_THE_PALM, true, gameTime, sentGlove, false);
        }

        /**
         * This lift held by Knives' charge, which floats the blob ahead of the
         * hand, following the hand, until it settles home.
         * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
         *
         * @return the lift, floating
         */
        Lift floating() {
            return new Lift(began, from, hops, lastStreamTick, sentGlove, true);
        }

        /**
         * The lift after a stream tick: a running spray runs on, a spray
         * that had ended turns the blob round from the pose it had reached
         * while it is still off the palm, and one whose blob is home hops
         * off the palm again.
         * AbilityPoses.heldAt is the shape: an ease reversing part way starts from where it got to
         *
         * @param gameTime  the game time of the stream tick
         * @param sentGlove the glove the server sent with it
         * @return the lift
         */
        Lift sprayedAt(long gameTime, Vec3 sentGlove) {
            if (runningAt(lastStreamTick, gameTime)) {
                return new Lift(began, from, hops, gameTime, sentGlove, floats);
            }
            if (standsAt(gameTime)) {
                return new Lift(gameTime, poseAt(gameTime), false, gameTime, sentGlove, floats);
            }
            return beginning(gameTime, sentGlove);
        }

        /** @return the game time the spray reads ended, a tick past the stream's gap */
        private double endedAt() {
            return lastStreamTick + STREAM_GAP_TICKS + 1.0;
        }

        /**
         * The blob's pose at a time.
         *
         * @param now the game time, with the frame's partial tick
         * @return the pose
         */
        PalmBlobHop.Pose poseAt(double now) {
            double ended = endedAt();
            PalmBlobHop.Pose reached = runningPoseAt(Math.min(now, ended) - began);
            return now < ended ? reached : PalmBlobHop.settlePoseAt(reached, now - ended);
        }

        private PalmBlobHop.Pose runningPoseAt(double ticks) {
            return hops ? PalmBlobHop.hopPoseAt(ticks) : PalmBlobHop.risePoseAt(from, ticks);
        }

        /**
         * The share lifted at a time.
         *
         * @param now the game time, with the frame's partial tick
         * @return the share, 0 on the palm to 1 at the source
         */
        float shareAt(double now) {
            return poseAt(now).share();
        }

        /**
         * Whether the blob is off the palm at a time: from the spray's start
         * until the settle brings it home.
         *
         * @param now the game time, with the frame's partial tick
         * @return true while the palm draws no blob
         */
        boolean standsAt(double now) {
            return now >= began && now < endedAt() + PalmBlobHop.SETTLE_TICKS;
        }
    }

    /**
     * A lifted blob to draw.
     *
     * @param place where it stands in the world
     * @param type  its goo type, the one the caster's glove has selected
     * @param shape its box against the palm blob's rest cube
     */
    public record Blob(Vec3 place, ResourceKey<GooTypeDefinition> type, BlobShape shape) {
    }

    /**
     * Marks a caster's cast held at a tick: a spray's stream tick that threw
     * drips, a tick the local press held a cast, or one the server passed on
     * for another caster.
     *
     * @param casterId  the caster's entity id
     * @param sentGlove the glove the tick came with
     * @param gameTime  the level's game time the tick reached this client
     */
    public static void held(int casterId, Vec3 sentGlove, long gameTime) {
        LIFTS.compute(casterId, (id, lift) -> lift == null ? Lift.beginning(gameTime, sentGlove)
                : lift.sprayedAt(gameTime, sentGlove));
    }

    /**
     * Marks a caster's Knives charge held at a tick, floating the blob ahead
     * of the hand: a blob resting on the palm hops off it from the tick the
     * charge began, so the lift stands from the wind-up's first tick.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the tick came with
     * @param chargeBegan the game time the caster's wind-up began
     * @param gameTime    the level's game time now
     */
    public static void floated(int casterId, Vec3 sentGlove, long chargeBegan, long gameTime) {
        LIFTS.compute(casterId, (id, lift) -> (standsOffThePalm(lift, gameTime) ? lift
                : Lift.beginning(Math.min(chargeBegan, gameTime), sentGlove))
                .sprayedAt(gameTime, sentGlove).floating());
    }

    /**
     * Whether a glove press lifts the palm blob: while a stream or a channel
     * runs from it, and while a release ability charges with the use key held.
     * every-ability-casts-from-the-lifted-palm-blob
     *
     * @param press the glove press
     * @return true while the press holds a cast
     */
    public static boolean pressLifts(GloveInputGate press) {
        return press.isArmed() && (press.isStreaming() || press.isPreviewing());
    }

    /**
     * Marks another caster's cast held at a tick the server passed on: a
     * stream or channel it ran, or a charge the caster's client held.
     * every-ability-casts-from-the-lifted-palm-blob
     *
     * @param payload  the held tick
     * @param gameTime the level's game time the tick reached this client
     */
    public static void liftArrived(PalmBlobLiftPayload payload, long gameTime) {
        held(payload.casterId(), payload.glove(), gameTime);
    }

    /**
     * Handles a held tick the server sent for a caster, on the client thread.
     *
     * @param payload the held tick
     * @param context the network context
     */
    public static void onPayload(PalmBlobLiftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                liftArrived(payload, mc.level.getGameTime());
            }
        });
    }

    /**
     * Whether a spray runs: its last stream tick lies within the stream's
     * gap before now. A tick stamped after now came from a level left behind.
     *
     * @param lastStreamTick the game time of the last stream tick
     * @param now            the game time now
     * @return true while the stream's ticks keep coming
     */
    static boolean runningAt(long lastStreamTick, long now) {
        return now >= lastStreamTick && now - lastStreamTick <= STREAM_GAP_TICKS;
    }

    /**
     * Whether a caster's spray runs now.
     *
     * @param casterId the caster's entity id
     * @param now      the game time now
     * @return true while the caster's stream ticks keep coming
     */
    public static boolean running(int casterId, long now) {
        Lift lift = LIFTS.get(casterId);
        return lift != null && runningAt(lift.lastStreamTick(), now);
    }

    /**
     * The lifted blob's size at the source: beating between its least and
     * greatest size, both above the palm blob's rest size, once a beat.
     *
     * @param time the game time in ticks, with the frame's partial tick
     * @return the size against the palm blob's rest size
     */
    static float liftedSizeAt(double time) {
        double swell = HALF - HALF * Math.cos(FULL_TURN * time / BEAT_TICKS);
        return (float) (LIFTED_LEAST + (LIFTED_GREATEST - LIFTED_LEAST) * swell);
    }

    /**
     * The blob's size at a share lifted: rest size on the palm, the lifted
     * size and its beat at the source.
     *
     * @param share the share lifted, 0 to 1
     * @param time  the game time in ticks, with the frame's partial tick
     * @return the size against the palm blob's rest size
     */
    static float sizeAt(float share, double time) {
        return Mth.lerp(share, KnivesCharge.PALM_REST, liftedSizeAt(time));
    }

    /**
     * The box a blob in a pose draws in: its shape at its size.
     * palm-blob-rests-pressed-and-stands-into-the-cube
     *
     * @param pose the blob's pose
     * @param time the game time in ticks, with the frame's partial tick
     * @return the box, against the palm blob's rest cube
     */
    static BlobShape drawnShapeAt(PalmBlobHop.Pose pose, double time) {
        return pose.shape().scaled(sizeAt(pose.share(), time));
    }

    /**
     * The box a lift's blob in a pose draws in: a spray's beats at its
     * lifted size, and Knives' floating blob keeps the palm blob's rest size
     * for the charge to swell.
     * charge-swells-the-palm-blob
     *
     * @param lift the lift
     * @param pose the blob's pose
     * @param time the game time in ticks, with the frame's partial tick
     * @return the box, against the palm blob's rest cube
     */
    static BlobShape drawnShapeAt(Lift lift, PalmBlobHop.Pose pose, double time) {
        return lift.floats() ? pose.shape().scaled(KnivesCharge.PALM_REST) : drawnShapeAt(pose, time);
    }

    /**
     * Where a lift's blob rises to: the float point ahead of the hand for
     * Knives' charge, and the rest point the selected ability names otherwise.
     *
     * @param casterId    the caster's entity id
     * @param lift        the caster's lift
     * @param partialTick the frame's partial tick
     * @param now         the game time, with the frame's partial tick
     * @return the source, in the world
     */
    private static Vec3 sourceOf(int casterId, Lift lift, float partialTick, double now) {
        // knives-blob-floats-ahead-of-the-hand-and-spews-the-knives: the blob follows the hand, backhand and all
        return lift.floats() ? BlobRest.floatingOf(casterId, lift.sentGlove(), partialTick,
                KnivesCharge.acrossOf(casterId, now))
                // centered-abilities-float-the-blob-below-the-crosshair: a centered ability rests on the center line
                : BlobRest.of(casterId, lift.sentGlove(), partialTick);
    }

    /**
     * A caster's blob's pose off the palm at a time.
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the frame's partial tick
     * @return the pose, empty once the blob rests on the palm
     */
    static Optional<PalmBlobHop.Pose> poseOf(int casterId, double now) {
        Lift lift = LIFTS.get(casterId);
        return standsOffThePalm(lift, now) ? Optional.of(lift.poseAt(now)) : Optional.empty();
    }

    /**
     * The blob's place at a share lifted, on the line from the palm to the source.
     *
     * @param share  the share lifted, 0 to 1
     * @param palm   where the glove's palm is
     * @param source where the spray source stands
     * @return the blob's place
     */
    static Vec3 placeAt(float share, Vec3 palm, Vec3 source) {
        return palm.lerp(source, share);
    }

    /**
     * Where a blob in a pose stands: on the line from where it stands up
     * off the palm to the source, so through the hop its bottom stays where
     * the palm blob's rests and its center rises only as it stretches.
     * every-lift-begins-with-a-hop
     *
     * @param pose   the blob's pose
     * @param palm   where the resting palm blob's center is
     * @param up     the blob's up, a rest half-edge long
     * @param source where the spray source stands
     * @return the blob's place
     */
    static Vec3 placeAt(PalmBlobHop.Pose pose, Vec3 palm, Vec3 up, Vec3 source) {
        return placeAt(pose.share(), palm.add(up.scale(pose.stand())), source);
    }


    /**
     * Where a caster's blob stands off the palm this frame.
     *
     * @param casterId    the caster's entity id
     * @param partialTick the frame's partial tick
     * @return the blob's place, empty once it has settled back onto the palm
     */
    private static Optional<Standing> standingOf(int casterId, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Lift lift = LIFTS.get(casterId);
        if (mc.level == null || lift == null || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return Optional.empty();
        }
        double now = mc.level.getGameTime() + (double) partialTick;
        if (!lift.standsAt(now)) {
            return Optional.empty();
        }
        PalmBlobHop.Pose pose = lift.poseAt(now);
        Vec3 palm = palmOf(mc, caster, partialTick, KnivesCharge.acrossOf(casterId, now));
        return Optional.of(new Standing(caster, placeAt(pose, palm, upOf(caster, partialTick),
                sourceOf(casterId, lift, partialTick, now)), drawnShapeAt(lift, pose, now), swellOf(caster, now)));
    }

    /**
     * A caster's blob's up in the world, a rest half-edge long: the level
     * draws it turned with the caster's look at a flying blob's palm size.
     *
     * @param caster      the caster
     * @param partialTick the frame's partial tick
     * @return the up
     */
    private static Vec3 upOf(Player caster, float partialTick) {
        Vector3f up = new Vector3f(0f, GooFlightRenderer.SHELL_HW * FLIGHT_SIZE_OF_PALM_REST, 0f)
                .rotate(facingTheLook(caster.getViewXRot(partialTick), caster.getViewYRot(partialTick)));
        return new Vec3(up);
    }

    /**
     * A caster's blob off the palm, before its goo type is read.
     *
     * @param caster the caster
     * @param place  where the blob stands in the world
     * @param shape  its box against the palm blob's rest cube, before the swell
     * @param swell  how far it swells against its lifted size
     */
    private record Standing(Player caster, Vec3 place, BlobShape shape, float swell) {
    }

    /**
     * How far a caster's lifted blob swells: as Knives' charge grows.
     *
     * @param caster the caster
     * @param now    the game time, with the frame's partial tick
     * @return the swell against the blob's lifted size
     */
    private static float swellOf(Player caster, double now) {
        return KnivesCharge.liftedSwellOf(caster, now);
    }

    /**
     * Where a caster's spray drip leaves: the caster's blob where it stands,
     * easing off the palm or at the source, and the spray source itself
     * where no blob stands.
     * palm-blob-lifts-as-the-spray-source: no drip shows until it leaves the blob
     *
     * @param casterId    the caster's entity id
     * @param sentGlove   the glove the server sent with the stream's tick
     * @param partialTick the partial tick the drip leaves at
     * @return where the drip leaves
     */
    public static Vec3 dripLeavesFrom(int casterId, Vec3 sentGlove, float partialTick) {
        return placeOf(casterId, partialTick).orElseGet(() -> SpraySource.of(casterId, sentGlove, partialTick));
    }

    /**
     * Where a caster's blob stands off the palm this frame.
     *
     * @param casterId    the caster's entity id
     * @param partialTick the frame's partial tick
     * @return the blob's place, empty while it rests on the palm
     */
    public static Optional<Vec3> placeOf(int casterId, float partialTick) {
        return standingOf(casterId, partialTick).map(Standing::place);
    }

    /**
     * The blob a caster shows off the palm this frame, in the glove's
     * selected goo type.
     *
     * @param casterId    the caster's entity id
     * @param partialTick the frame's partial tick
     * @return the blob, empty once it has settled back onto the palm or where the glove has no type selected
     */
    static Optional<Blob> blobOf(int casterId, float partialTick) {
        return standingOf(casterId, partialTick).flatMap(standing -> Optional.ofNullable(
                GooGloveItem.getSelectedType(gloveOf(standing.caster())))
                .map(type -> new Blob(standing.place(), type, standing.shape().scaled(standing.swell()))));
    }

    /**
     * Whether a caster's blob stands off the palm at a time.
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the frame's partial tick
     * @return true from the lift's start until the settle brings the blob home
     */
    public static boolean standsOff(int casterId, double now) {
        return standsOffThePalm(LIFTS.get(casterId), now);
    }

    /**
     * The local player's blob as the hand's pass draws it: the share lifted,
     * the spray source carried into the hand pass, the shape, and how far
     * its center stands above the resting palm blob's.
     * palm-blob-lifts-as-the-spray-source: drawn in the hand's pass, the blob draws over the glove
     *
     * @param share        the share lifted, 0 on the palm to 1 at the source
     * @param sourceInHand the spray source in the hand pass's camera-relative world axes
     * @param shape        the blob's box against the palm blob's rest cube
     * @param stand        the center's rise above the resting palm blob's along the blob's up, in rest half-edges
     * @param facing       the turn facing the blob along the look once off the palm, in the hand pass's world
     *                     axes, or null where it turns with the glove
     */
    public record HandLift(float share, Vec3 sourceInHand, BlobShape shape, float stand,
                           @Nullable Quaternionf facing) {
    }

    /**
     * The local first-person player's blob off the palm, as the glove held
     * before the eyes draws it in the hand's pass.
     *
     * @return the lift, empty once the blob has settled back onto the palm
     */
    public static Optional<HandLift> liftHeldInView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return Optional.empty();
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Lift lift = LIFTS.get(mc.player.getId());
        double now = mc.level.getGameTime() + (double) partial;
        if (lift == null || !lift.standsAt(now)) {
            return Optional.empty();
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 source = sourceOf(mc.player.getId(), lift, partial, now).subtract(camera.position());
        PalmBlobHop.Pose pose = lift.poseAt(now);
        // knives-blob-floats-ahead-of-the-hand-and-spews-the-knives: facing forward with the look, not the hand
        Quaternionf facing = lift.floats() ? facingTheLook(camera.xRot(), camera.yRot()) : null;
        return Optional.of(new HandLift(pose.share(), HandPassView.toHandPass(source, camera.rotation(),
                camera.getFov(), HandPassView.frameBob(mc)), drawnShapeAt(lift, pose, now)
                .scaled(swellOf(mc.player, now)), pose.stand(), facing));
    }

    /**
     * Whether a glove is the one the local player holds before the eyes in
     * first person, whose lifted blob the hand's pass draws.
     *
     * @param glove the glove being drawn
     * @return true for either hand's stack of the local player in first person
     */
    public static boolean heldInView(ItemStack glove) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType().isFirstPerson()
                && (glove == mc.player.getMainHandItem() || glove == mc.player.getOffhandItem());
    }

    /**
     * Where a caster's glove palm is: where the first-person glove last drew
     * its palm, carried out of the hand's pass, for the local player in first
     * person, and the hand worked from the body otherwise.
     *
     * @param mc          the client
     * @param caster      the caster
     * @param partialTick the frame's partial tick
     * @param across      how far the caster's hand has eased across the chest, 0 to 1
     * @return the palm's place in the world
     */
    private static Vec3 palmOf(Minecraft mc, Player caster, float partialTick, float across) {
        Vec3 drawn = GloveSpecialRenderer.getLastGooCenterCamRel();
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson() && drawn != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return camera.position().add(HandPassView.fromHandPass(drawn, camera.rotation(), camera.getFov(),
                    HandPassView.frameBob(mc)));
        }
        return KnivesCharge.handAt(caster, KnifeRainSling.gloveOnTheRight(caster), across, partialTick);
    }

    /**
     * Whether a glove's palm blob is off the palm: a player in the level
     * holds this glove and that player's blob has not settled back.
     * charge-swells-the-palm-blob: the palm blob is the one blob the glove ever shows
     *
     * @param glove the glove being drawn
     * @return true where the palm draws no blob
     */
    public static boolean liftedFrom(ItemStack glove) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LIFTS.isEmpty()) {
            return false;
        }
        long now = mc.level.getGameTime();
        return mc.level.players().stream().anyMatch(player -> gloveOf(player) == glove && standsOffThePalm(
                LIFTS.get(player.getId()), now));
    }

    private static boolean standsOffThePalm(@Nullable Lift lift, double now) {
        return lift != null && lift.standsAt(now);
    }

    /**
     * The glove a player holds: the main hand's, or the off hand's where the
     * main hand holds none.
     *
     * @param player the player
     * @return the glove stack, or the off hand's stack where neither hand holds a glove
     */
    private static ItemStack gloveOf(Player player) {
        ItemStack main = player.getMainHandItem();
        return main.getItem() instanceof GooGloveItem ? main : player.getOffhandItem();
    }

    /**
     * Whether the level's pass draws a caster's blob: every caster's but the
     * local player's in first person, whose blob the hand's pass draws over
     * the glove, though a view through a portal sees that one in the level too.
     *
     * @param casterId the caster's entity id
     * @return true where the level draws the blob
     */
    static boolean drawsInTheLevel(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || mc.player.getId() != casterId || !mc.options.getCameraType().isFirstPerson()
                || PortalView.drawing();
    }

    /**
     * The turn that faces a blob along a look, its front from +z onto the
     * look and its top from +y up the view.
     *
     * @param xRot the look's pitch in degrees, positive looking down
     * @param yRot the look's yaw in degrees
     * @return the turn
     */
    public static Quaternionf facingTheLook(float xRot, float yRot) {
        return new Quaternionf().rotationY((float) Math.toRadians(-yRot)).rotateX((float) Math.toRadians(xRot));
    }

    /**
     * Draws every caster's blob off the palm that the level's pass draws,
     * turned with that caster's look, and forgets each lift that has settled.
     * palm-blob-draws-as-the-charge-blob: the lifted blob is the charge-style core in its shell
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LIFTS.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        LIFTS.values().removeIf(lift -> !lift.standsAt(now));
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = PortalView.drawing() ? PortalView.camera() : mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Integer casterId : LIFTS.keySet()) {
            if (drawsInTheLevel(casterId)) {
                drawnInTheLevel(mc, casterId, partial).ifPresent(drawn -> drawBlob(event.getPoseStack(), buffers,
                        drawn, camera.position(), now + partial));
            }
        }
        buffers.endBatch();
    }

    /**
     * A caster's blob as the level draws it, turned with the caster's look.
     *
     * @param mc       the client
     * @param casterId the caster's entity id
     * @param partial  the frame's partial tick
     * @return the blob, empty where it has settled or the caster is gone
     */
    private static Optional<Drawn> drawnInTheLevel(Minecraft mc, int casterId, float partial) {
        if (!(mc.level.getEntity(casterId) instanceof Player caster)) {
            return Optional.empty();
        }
        Quaternionf facing = facingOf(caster, partial);
        return blobOf(casterId, partial).map(blob -> new Drawn(blob, facing));
    }

    /**
     * The turn the level's pass draws a caster's blob in: facing along the caster's look.
     * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives: facing forward with the look
     *
     * @param caster  the caster
     * @param partial the frame's partial tick
     * @return the turn
     */
    static Quaternionf facingOf(Player caster, float partial) {
        return facingTheLook(caster.getViewXRot(partial), caster.getViewYRot(partial));
    }

    /**
     * A blob as the level draws it.
     *
     * @param blob   the blob
     * @param facing the turn that faces it along its caster's look
     */
    private record Drawn(Blob blob, Quaternionf facing) {
    }

    private static void drawBlob(PoseStack pose, MultiBufferSource buffers, Drawn drawn, Vec3 camera, float time) {
        Vec3 place = drawn.blob().place();
        pose.pushPose();
        pose.translate(place.x - camera.x, place.y - camera.y, place.z - camera.z);
        pose.mulPose(drawn.facing());
        GooFlightRenderer.renderBlob(pose, buffers, drawn.blob().type(), time,
                drawn.blob().shape().scaled(FLIGHT_SIZE_OF_PALM_REST));
        pose.popPose();
    }

    /** Forgets every lift, as a test's end does. */
    static void forgetEverySpray() {
        LIFTS.clear();
    }
}

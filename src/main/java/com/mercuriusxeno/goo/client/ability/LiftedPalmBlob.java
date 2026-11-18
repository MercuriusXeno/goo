package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GloveInputGate;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooCasters;
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
 * held, the blob hops off the palm to its rest point (BlobRest): straight
 * above where the hand rests, the glove rest for Bore, or the center line
 * below the crosshair for a centered ability, shrinking from the pancake spread over the palm into a
 * smaller cube as it goes, and stands there in the goo type the glove has selected, beating
 * between two sizes below the pancake's while the cast is held. Once the
 * held ticks stop for longer than a stream's gap it comes back down and
 * smears across the palm into the pancake. The glove's palm draws none
 * while the blob is off it, so the caster shows one blob, and each Goop or
 * Slick drip leaves the blob where it stands. The local player's lift reads
 * the glove press; another caster's reads the held ticks the server passes
 * on. The local first-person player's blob draws in the hand's pass, over
 * the glove and turned with it; every other caster's draws in the level,
 * turned with that caster's look.
 * palm-blob-lifts-as-the-spray-source
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
    /**
     * The ticks one beat of the lifted blob's swell takes: 12, the length of a Blast stage.
     * decision every-lifted-palm-blob-beats-once-a-second
     */
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
    /** The blob's front as {@link #facingTheLook} turns it: along the look. */
    private static final Vec3 ALONG_THE_LOOK = new Vec3(0, 0, 1);
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;

    /** Each lifting caster's lift, by entity id. */
    private static final Map<Integer, Lift> LIFTS = new ConcurrentHashMap<>();
    /** The game tick a charge-sized hold the beat clocks on began, while one runs. */
    private static long chargeBegan;
    /** The ticks a stage of that hold takes, 0 while the beat runs on the game clock. */
    private static double chargeStageTicks;

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
     */
    record Lift(long began, PalmBlobHop.Pose from, boolean hops, long lastStreamTick, Vec3 sentGlove) {

        /**
         * A lift from the palm as a spray begins.
         *
         * @param gameTime  the game time of the spray's first stream tick
         * @param sentGlove the glove the server sent with that tick
         * @return the lift
         */
        static Lift beginning(long gameTime, Vec3 sentGlove) {
            return new Lift(gameTime, PalmBlobHop.Pose.ON_THE_PALM, true, gameTime, sentGlove);
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
                return new Lift(began, from, hops, gameTime, sentGlove);
            }
            if (standsAt(gameTime)) {
                return new Lift(gameTime, poseAt(gameTime), false, gameTime, sentGlove);
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
     * @param place   where it stands in the world
     * @param type    its goo type, the one the caster's glove has selected
     * @param shape   its box against the palm blob's rest cube
     * @param form    the travel form it becomes in the hand, the blob for none
     * @param javelin how far it has become that form, 0 the cuboid to 1 the form whole
     */
    public record Blob(Vec3 place, ResourceKey<GooTypeDefinition> type, BlobShape shape, TravelForm form,
                       float javelin) {
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
     * Lifts a caster's palm blob afresh from the palm, hopping, as a held
     * javelin leaves the hand with right click still held, so the next
     * javelin forms from the blob behind it.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param casterId  the caster's entity id
     * @param sentGlove the glove the throw left from
     * @param gameTime  the level's game time of the throw
     */
    public static void liftAfresh(int casterId, Vec3 sentGlove, long gameTime) {
        LIFTS.put(casterId, Lift.beginning(gameTime, sentGlove));
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
     * Where the lifted blob stands in its beat at a moment, the one clock a
     * charging Blast's spheroid beats on too.
     * decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob
     *
     * @param time the game time in ticks, with the frame's partial tick
     * @return the beat's phase, 0 to 1
     */
    static double beatPhase(double time) {
        if (chargeStageTicks > 0) {
            return chargeBeatPhase(chargeBegan, time, chargeStageTicks);
        }
        return fraction(time / BEAT_TICKS);
    }

    /**
     * Where a charge-sized hold's beat stands: one beat a stage, clocked on
     * the hold's own ticks, at its greatest swell on each stage step and its
     * least midway, whatever game tick the hold began on.
     * decision every-lifted-palm-blob-beats-once-a-second
     *
     * @param began      the game tick the hold began on
     * @param now        the game time, partial ticks included
     * @param stageTicks the ticks one stage of the charge takes
     * @return the beat's phase, 0 to 1, the half beat on each step
     */
    static double chargeBeatPhase(long began, double now, double stageTicks) {
        return fraction((now - began) / stageTicks + HALF);
    }

    /**
     * Clocks every lifted blob's beat on a charge-sized hold while it runs.
     *
     * @param began      the game tick the hold began on
     * @param stageTicks the ticks one stage of the charge takes
     */
    public static void clockOnCharge(long began, double stageTicks) {
        chargeBegan = began;
        chargeStageTicks = stageTicks;
    }

    /** Returns the beat to the game clock, as a charge-sized hold ends. */
    public static void clockOnGameTime() {
        chargeStageTicks = 0;
    }

    private static double fraction(double beats) {
        return beats - Math.floor(beats);
    }

    /**
     * The lifted blob's size at the source: beating between its least and
     * greatest size, both above the palm blob's rest size, once a beat.
     *
     * @param time the game time in ticks, with the frame's partial tick
     * @return the size against the palm blob's rest size
     */
    static float liftedSizeAt(double time) {
        return liftedSizeAtPhase(beatPhase(time));
    }

    /**
     * The lifted blob's size at a point of its beat: least at the beat's
     * start, greatest at its middle.
     *
     * @param phase the beat's phase, 0 to 1
     * @return the size against the palm blob's rest size
     */
    static float liftedSizeAtPhase(double phase) {
        double swell = HALF - HALF * Math.cos(FULL_TURN * phase);
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
        return Mth.lerp(share, 1f, liftedSizeAt(time));
    }

    /**
     * The cube a caster's held stack leaves standing, against its full size:
     * a step smaller for each knife or javelin the server counts held, easing
     * to each smaller step over the ticks the one forming takes to rise, gone
     * once the stack is full, and whole for a caster holding none.
     * decision knives-blob-shrinks-per-knife-and-vanishes-on-the-last
     * decision dart-javelins-hover-over-the-shrinking-blob-cube
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the frame's partial tick
     * @return the share of the full cube
     */
    static float stackShareOf(int casterId, double now) {
        // palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume: the cube draws at the buffer's fill, by volume
        if (ClientPalmBuffers.holdsABuffer(casterId)) {
            double fill = ClientPalmBuffers.fillAt(casterId, now) + leavingShareOf(casterId, now);
            return (float) (StackHover.edgeAtFill(fill) / StackHover.edgeAtFill(1));
        }
        HoveringKnives.Fan fan = HoveringKnives.fanOf(casterId);
        if (fan != null) {
            return easedShare(fan.previous(), fan.count(), StackHover.KNIVES, now - fan.changedAt());
        }
        HeldJavelins.Stack stack = HeldJavelins.stackOf(casterId);
        return stack == null ? 1f
                : easedShare(stack.previous(), stack.count(), StackHover.JAVELINS, now - stack.changedAt());
    }

    /**
     * The share of the blob a forming weapon's cuboid has still to carry away:
     * the share the server took as the weapon formed, given back to the drawn
     * blob and eased away as the cuboid leaves it, so the blob shrinks by ease
     * and blob and cuboid together hold one volume throughout.
     * decision weapons-form-from-a-goo-cuboid-leaving-the-palm-blob
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the frame's partial tick
     * @return the share still leaving, 0 to 1
     */
    static double leavingShareOf(int casterId, double now) {
        HoveringKnives.Fan fan = HoveringKnives.fanOf(casterId);
        if (fan != null) {
            double age = now - fan.changedAt();
            return Math.max(0, fan.count() - fan.previous()) * StackHover.shareOf(StackHover.KNIVES)
                    * (1 - HoveringKnives.easedAt(age));
        }
        HeldJavelins.Stack stack = HeldJavelins.stackOf(casterId);
        if (stack == null) {
            return 0;
        }
        return Math.max(0, stack.count() - stack.previous()) * StackHover.shareOf(StackHover.JAVELINS)
                * (1 - HeldJavelins.formedAt(now - stack.changedAt()));
    }

    /**
     * The size the palm blob rests at on a caster's palm: the local player's
     * at its buffer's fill by volume, for the stack the local player casts
     * from, and whole for any other.
     * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
     *
     * @param caster the caster stack being drawn
     * @return the share of the full blob
     */
    public static float restingShareOf(ItemStack caster) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !GooCasters.drawnAsCaster(mc.player, caster)) {
            return 1f;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return (float) (StackHover.edgeAtFill(ClientPalmBuffers.fillAt(mc.player.getId(), now))
                / StackHover.edgeAtFill(1));
    }

    // The cube's share eased from the count before the stack's last change to the count after, as one rises.
    private static float easedShare(int previous, int count, int stack, double age) {
        return Mth.lerp(HoveringKnives.easedAt(age), StackHover.cubeShareAt(previous, stack),
                StackHover.cubeShareAt(count, stack));
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
     * Where a lift's blob rises to: the place Bore's blob eases to from its
     * rest while a bore runs, and the rest point the selected ability names
     * otherwise.
     *
     * @param casterId    the caster's entity id
     * @param lift        the caster's lift
     * @param partialTick the frame's partial tick
     * @param now         the game time, with the frame's partial tick
     * @return the source, in the world
     */
    private static Vec3 sourceOf(int casterId, Lift lift, float partialTick, double now) {
        // centered-abilities-float-the-blob-below-the-crosshair: a centered ability rests on the center line
        // bore-blob-eases-to-place-the-drill-at-the-crosshair: Bore's blob eases forward from that rest
        return BoreBlobPlace.of(casterId, BlobRest.of(casterId, lift.sentGlove(), partialTick), partialTick, now);
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
        Vec3 palm = palmOf(mc, caster, partialTick);
        return Optional.of(new Standing(caster, placeAt(pose, palm, upOf(caster, partialTick),
                sourceOf(casterId, lift, partialTick, now)),
                drawnShapeAt(pose, now).scaled(stackShareOf(casterId, now)),
                pose.share()));
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
     * @param shape  its box against the palm blob's rest cube
     * @param share  the share it has lifted off the palm, 0 to 1
     */
    private record Standing(Player caster, Vec3 place, BlobShape shape, float share) {
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
     * The blob a caster shows off the palm this frame, in the selected goo
     * type of the caster's glove, or of the raw goo held bare handed.
     * decision raw-goo-items-work-bare-handed-for-the-held-type
     *
     * @param casterId    the caster's entity id
     * @param partialTick the frame's partial tick
     * @return the blob, empty once it has settled back onto the palm or where the caster has no type selected
     */
    static Optional<Blob> blobOf(int casterId, float partialTick) {
        return standingOf(casterId, partialTick).flatMap(standing -> {
            ItemStack caster = GooCasters.casterOf(standing.caster());
            Delivery delivery = PalmJavelin.selectedDelivery(caster);
            // shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob: Dart's blob becomes the javelin as it lifts
            float javelin = PalmJavelin.morphAt(delivery, standing.share());
            TravelForm form = PalmJavelin.formOf(delivery);
            return Optional.ofNullable(GooCasters.selectedTypeOf(caster)).map(type -> new Blob(standing.place(),
                    type, standing.shape(), form, javelin));
        });
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
     * @param form         the travel form the blob becomes in the hand, the blob for none
     * @param javelin      how far the blob has become that form, 0 the cuboid to 1 the form whole
     */
    public record HandLift(float share, Vec3 sourceInHand, BlobShape shape, float stand,
                           @Nullable Quaternionf facing, TravelForm form, float javelin) {

        /**
         * A lift whose blob stays a cuboid.
         *
         * @param share        the share lifted, 0 on the palm to 1 at the source
         * @param sourceInHand the spray source in the hand pass's camera-relative world axes
         * @param shape        the blob's box against the palm blob's rest cube
         * @param stand        the center's rise above the resting palm blob's along the blob's up
         * @param facing       the turn facing the blob along the look once off the palm, or null
         */
        public HandLift(float share, Vec3 sourceInHand, BlobShape shape, float stand, @Nullable Quaternionf facing) {
            this(share, sourceInHand, shape, stand, facing, TravelForm.BLOB, 0f);
        }
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
        Delivery delivery = PalmJavelin.selectedDelivery(GooCasters.casterOf(mc.player));
        float javelin = PalmJavelin.morphAt(delivery, pose.share());
        return Optional.of(new HandLift(pose.share(), HandPassView.toHandPass(source, camera.rotation(),
                camera.getFov(), HandPassView.frameBob(mc)),
                drawnShapeAt(pose, now).scaled(stackShareOf(mc.player.getId(), now)), pose.stand(),
                facingInView(javelin, camera), PalmJavelin.formOf(delivery), javelin));
    }

    /**
     * The turn facing a lifted blob along the look in the hand pass, where
     * it has begun to become Dart's javelin.
     * shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob
     *
     * @param javelin how far the blob has become its travel form, 0 to 1
     * @param camera  the main camera
     * @return the turn, or null where the blob turns with the glove
     */
    private static @Nullable Quaternionf facingInView(float javelin, Camera camera) {
        return javelin > 0f ? facingTheLook(camera.xRot(), camera.yRot()) : null;
    }

    /**
     * Whether a glove is the one the local player holds before the eyes in
     * first person, whose lifted blob the hand's pass draws above its palm.
     *
     * @param glove the glove being drawn
     * @return true for the local player's glove in first person, the main hand's where both hands hold one
     */
    public static boolean heldInView(ItemStack glove) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType().isFirstPerson()
                && GooCasters.drawnAsCaster(mc.player, glove);
    }

    /**
     * Where a caster's glove palm is: where the first-person glove last drew
     * its palm, carried out of the hand's pass, for the local player in first
     * person, and the hand worked from the body otherwise.
     *
     * @param mc          the client
     * @param caster      the caster
     * @param partialTick the frame's partial tick
     * @return the palm's place in the world
     */
    private static Vec3 palmOf(Minecraft mc, Player caster, float partialTick) {
        Vec3 drawn = GloveSpecialRenderer.getLastGooCenterCamRel();
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson() && drawn != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return camera.position().add(HandPassView.fromHandPass(drawn, camera.rotation(), camera.getFov(),
                    HandPassView.frameBob(mc)));
        }
        return KnivesCharge.handAt(caster, GooCasters.casterOnTheRight(caster), partialTick);
    }

    /**
     * Whether a caster's palm blob is off the palm: a player in the level
     * casts from this stack, a glove or raw goo held bare handed, and that
     * player's blob has not settled back.
     * charge-swells-the-palm-blob: the palm blob is the one blob the glove ever shows
     *
     * @param caster the caster stack being drawn
     * @return true where the palm draws no blob
     */
    public static boolean liftedFrom(ItemStack caster) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LIFTS.isEmpty()) {
            return false;
        }
        long now = mc.level.getGameTime();
        return mc.level.players().stream().anyMatch(player -> GooCasters.drawnAsCaster(player, caster) && standsOffThePalm(
                LIFTS.get(player.getId()), now));
    }

    private static boolean standsOffThePalm(@Nullable Lift lift, double now) {
        return lift != null && lift.standsAt(now);
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
        Blob blob = drawn.blob();
        Vec3 place = blob.place();
        pose.pushPose();
        pose.translate(place.x - camera.x, place.y - camera.y, place.z - camera.z);
        pose.mulPose(drawn.facing());
        if (blob.javelin() < 1f) {
            GooFlightRenderer.renderBlob(pose, buffers, blob.type(), time,
                    blob.shape().scaled(FLIGHT_SIZE_OF_PALM_REST * (1f - blob.javelin())));
        }
        if (blob.javelin() > 0f) {
            // shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob: the javelin points along the caster's look
            pose.scale(FLIGHT_SIZE_OF_PALM_REST, FLIGHT_SIZE_OF_PALM_REST, FLIGHT_SIZE_OF_PALM_REST);
            GooFlightRenderer.emitTravelForm(pose.last(), buffers.getBuffer(GooSubmitter.renderType()), blob.type(),
                    blob.form(), ALONG_THE_LOOK, blob.javelin());
        }
        pose.popPose();
    }

    /** Forgets every lift, as a test's end does. */
    static void forgetEverySpray() {
        LIFTS.clear();
    }
}

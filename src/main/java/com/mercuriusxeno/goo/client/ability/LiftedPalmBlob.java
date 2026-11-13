package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.model.GloveSpecialRenderer;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.KnifeRainSling;
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
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The palm blob lifted off the glove to a channel's source: as a caster's
 * Goop or Slick spray begins, the blob eases off the palm to the spray
 * source, swelling from its rest size and standing up from pressed flat into
 * the cube as it goes, and stands there in the
 * goo type the glove has selected, beating between two sizes above its rest
 * size while the spray runs. Once the stream's ticks stop for longer than
 * the stream's gap it eases back onto the palm and shrinks to rest size. The
 * glove's palm draws none while the blob is off it, so the caster shows one
 * blob, and each spray drip leaves the blob where it stands. The local
 * first-person player's blob draws in the hand's pass, over the glove and
 * turned with it; every other caster's draws in the level, turned with that
 * caster's look. Kept apart from the spray so a later channel can lift the
 * blob the same way.
 * palm-blob-lifts-as-the-spray-source
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LiftedPalmBlob {

    /**
     * The most game ticks a stream tick may land after the last and still
     * keep the spray running: the gap a server hold continues across.
     */
    public static final int STREAM_GAP_TICKS = StreamHolds.LATEST_CONTINUING_GAP;
    /** The ticks the blob takes to ease off the palm to the source, and to settle back. */
    public static final int EASE_TICKS = 6;
    /** The ticks one beat of the lifted blob's swell takes. */
    static final double BEAT_TICKS = 12;
    /** The lifted blob's least and greatest size against the palm blob's rest size. */
    static final float LIFTED_LEAST = 1.05f;
    static final float LIFTED_GREATEST = 1.2f;
    /**
     * A flying blob's size the palm blob at rest shows as in the world: the
     * first-person glove draws its model at half size, so its rest blob reads
     * half a flying blob's width.
     */
    static final float FLIGHT_SIZE_OF_PALM_REST = 0.5f;
    /**
     * The pressed palm blob's width and depth against the cube it stands
     * into; its height is what holds the cube's volume. At rest the shell
     * stands just inside the hand's six-pixel width, 5.76 pixels wide.
     * operator ruling 2026-11-13: at 1.25 the shell's sides sat flush with the gauntlet's and z-fought;
     * they stand just inside the hand
     */
    static final float PRESSED_SPREAD = 1.2f;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;
    /** The smoothstep's terms, 3s² - 2s³, slow at both ends of an ease. */
    private static final float SMOOTHSTEP_RISE = 3f;
    private static final float SMOOTHSTEP_EASE = 2f;

    /** Each lifting caster's lift, by entity id. */
    private static final Map<Integer, Lift> LIFTS = new ConcurrentHashMap<>();

    private LiftedPalmBlob() {
    }

    /**
     * A caster's blob off the palm: the share lifted, 0 on the palm to 1 at
     * the source, eases up from where it stood as the spray began and back
     * down once the spray ends, a pure function of the time.
     *
     * @param began          the game time the spray began
     * @param from           the share the blob stood at as the spray began, above 0 where it began mid-settle
     * @param lastStreamTick the game time of the caster's last stream tick that threw drips
     * @param sentGlove      the glove the server sent with that tick
     */
    record Lift(long began, float from, long lastStreamTick, Vec3 sentGlove) {

        /**
         * A lift as a spray begins.
         *
         * @param gameTime  the game time of the spray's first stream tick
         * @param from      the share the blob stands at then
         * @param sentGlove the glove the server sent with that tick
         * @return the lift
         */
        static Lift beginning(long gameTime, float from, Vec3 sentGlove) {
            return new Lift(gameTime, from, gameTime, sentGlove);
        }

        /**
         * The lift after a stream tick: a running spray runs on, and a spray
         * that had ended begins again from the share the blob had reached,
         * so a blob settling turns round where it stands.
         * AbilityPoses.heldAt is the shape: an ease reversing part way starts from where it got to
         *
         * @param gameTime  the game time of the stream tick
         * @param sentGlove the glove the server sent with it
         * @return the lift
         */
        Lift sprayedAt(long gameTime, Vec3 sentGlove) {
            if (runningAt(lastStreamTick, gameTime)) {
                return new Lift(began, from, gameTime, sentGlove);
            }
            return beginning(gameTime, standsAt(gameTime) ? shareAt(gameTime) : 0f, sentGlove);
        }

        /** @return the game time the spray reads ended, a tick past the stream's gap */
        private double endedAt() {
            return lastStreamTick + STREAM_GAP_TICKS + 1.0;
        }

        /**
         * The share lifted at a time.
         *
         * @param now the game time, with the frame's partial tick
         * @return the share, 0 on the palm to 1 at the source
         */
        float shareAt(double now) {
            double ended = endedAt();
            float risen = Mth.lerp(eased((Math.min(now, ended) - began) / EASE_TICKS), from, 1f);
            return now < ended ? risen : risen * (1f - eased((now - ended) / EASE_TICKS));
        }

        /**
         * Whether the blob is off the palm at a time: from the spray's start
         * until the settle brings it home.
         *
         * @param now the game time, with the frame's partial tick
         * @return true while the palm draws no blob
         */
        boolean standsAt(double now) {
            return now >= began && now < endedAt() + EASE_TICKS;
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
     * An ease's progress, slow at both ends.
     *
     * @param share the share of the ease's ticks gone, clamped to 0 to 1
     * @return the eased share
     */
    private static float eased(double share) {
        float clamped = (float) Math.clamp(share, 0.0, 1.0);
        return clamped * clamped * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * clamped);
    }

    /**
     * Marks a caster's spray running at a stream tick that threw drips.
     *
     * @param casterId  the caster's entity id
     * @param sentGlove the glove the server sent with the stream's tick
     * @param gameTime  the level's game time the tick reached this client
     */
    public static void sprayed(int casterId, Vec3 sentGlove, long gameTime) {
        LIFTS.compute(casterId, (id, lift) -> lift == null ? Lift.beginning(gameTime, 0f, sentGlove)
                : lift.sprayedAt(gameTime, sentGlove));
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
     * The blob's shape at a share lifted, before its size: pressed flat on
     * the palm, wider and deeper than a cube and shorter, narrowing and
     * growing taller as it rises until it is the cube at the source. The
     * width and depth fall from the pressed spread by the same power the
     * height rises, so the product of the three is one at every share.
     * palm-blob-rests-pressed-and-stands-into-the-cube
     *
     * @param share the share lifted, 0 on the palm to 1 at the source
     * @return the shape against a cube of the blob's size
     */
    public static BlobShape shapeAt(float share) {
        float spread = (float) Math.pow(PRESSED_SPREAD, 1f - Math.clamp(share, 0f, 1f));
        return new BlobShape(spread, spread, 1f / (spread * spread));
    }

    /**
     * The blob's shape and size at a share lifted.
     *
     * @param share the share lifted, 0 to 1
     * @param time  the game time in ticks, with the frame's partial tick
     * @return the box it draws in, against the palm blob's rest cube
     */
    static BlobShape drawnShapeAt(float share, double time) {
        return shapeAt(share).scaled(sizeAt(share, time));
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
        float share = lift.shareAt(now);
        Vec3 source = SpraySource.of(casterId, lift.sentGlove(), partialTick);
        return Optional.of(new Standing(caster, placeAt(share, palmOf(mc, caster, partialTick), source), share,
                now));
    }

    /**
     * A caster's blob off the palm, before its goo type is read.
     *
     * @param caster the caster
     * @param place  where the blob stands in the world
     * @param share  the share lifted, 0 on the palm to 1 at the source
     * @param now    the game time, with the frame's partial tick
     */
    private record Standing(Player caster, Vec3 place, float share, double now) {
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
        return standingOf(casterId, partialTick).map(Standing::place)
                .orElseGet(() -> SpraySource.of(casterId, sentGlove, partialTick));
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
                .map(type -> new Blob(standing.place(), type, drawnShapeAt(standing.share(), standing.now()))));
    }

    /**
     * The local player's blob as the hand's pass draws it: the share lifted,
     * the spray source carried into the hand pass, and the shape.
     * palm-blob-lifts-as-the-spray-source: drawn in the hand's pass, the blob draws over the glove
     *
     * @param share        the share lifted, 0 on the palm to 1 at the source
     * @param sourceInHand the spray source in the hand pass's camera-relative world axes
     * @param shape        the blob's box against the palm blob's rest cube
     */
    public record HandLift(float share, Vec3 sourceInHand, BlobShape shape) {
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
        Vec3 source = SpraySource.of(mc.player.getId(), lift.sentGlove(), partial).subtract(camera.position());
        float share = lift.shareAt(now);
        return Optional.of(new HandLift(share, HandPassView.toHandPass(source, camera.rotation(), camera.getFov()),
                drawnShapeAt(share, now)));
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
     * @return the palm's place in the world
     */
    private static Vec3 palmOf(Minecraft mc, Player caster, float partialTick) {
        Vec3 drawn = GloveSpecialRenderer.getLastGooCenterCamRel();
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson() && drawn != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            return camera.position().add(HandPassView.fromHandPass(drawn, camera.rotation(), camera.getFov()));
        }
        return KnivesCharge.handAt(caster, KnifeRainSling.gloveOnTheRight(caster), 0f, partialTick);
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

    private static boolean standsOffThePalm(@Nullable Lift lift, long now) {
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
    static Quaternionf facingTheLook(float xRot, float yRot) {
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
        Quaternionf facing = facingTheLook(caster.getViewXRot(partial), caster.getViewYRot(partial));
        return blobOf(casterId, partial).map(blob -> new Drawn(blob, facing));
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

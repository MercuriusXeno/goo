package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.StreamHolds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The palm blob lifted off the glove to a channel's source: while a caster's
 * Goop or Slick spray runs, the blob stands at the spray source in the goo
 * type the glove has selected, larger than the palm blob at rest and beating
 * between two sizes, and the glove's palm draws none, so the caster shows
 * one blob. The spray runs from each stream tick that throws drips until the
 * stream's ticks stop for longer than the stream's gap. Kept apart from the
 * spray so a later channel can lift the blob the same way.
 * palm-blob-lifts-as-the-spray-source
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LiftedPalmBlob {

    /**
     * The most game ticks a stream tick may land after the last and still
     * keep the spray running: the gap a server hold continues across.
     */
    static final int STREAM_GAP_TICKS = StreamHolds.LATEST_CONTINUING_GAP;
    /** The ticks one beat of the lifted blob's swell takes. */
    static final double BEAT_TICKS = 12;
    /** The lifted blob's least and greatest size against the palm blob's rest size. */
    static final float LIFTED_LEAST = 1.25f;
    static final float LIFTED_GREATEST = 1.5f;
    /**
     * A flying blob's size the palm blob at rest shows as in the world: the
     * first-person glove draws its model at half size, so its rest blob reads
     * half a flying blob's width.
     */
    static final float FLIGHT_SIZE_OF_PALM_REST = 0.5f;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;

    /** Each spraying caster's last stream tick, by entity id. */
    private static final Map<Integer, Spray> SPRAYS = new ConcurrentHashMap<>();

    private LiftedPalmBlob() {
    }

    /**
     * A caster's spray as its stream ticks left it.
     *
     * @param lastStreamTick the game time of the caster's last stream tick that threw drips
     * @param sentGlove      the glove the server sent with that tick
     */
    private record Spray(long lastStreamTick, Vec3 sentGlove) {
    }

    /**
     * A lifted blob to draw.
     *
     * @param place where it stands in the world
     * @param type  its goo type, the one the caster's glove has selected
     * @param size  its size against the palm blob's rest size
     */
    public record Blob(Vec3 place, ResourceKey<GooTypeDefinition> type, float size) {
    }

    /**
     * Marks a caster's spray running at a stream tick that threw drips.
     *
     * @param casterId  the caster's entity id
     * @param sentGlove the glove the server sent with the stream's tick
     * @param gameTime  the level's game time the tick reached this client
     */
    public static void sprayed(int casterId, Vec3 sentGlove, long gameTime) {
        SPRAYS.put(casterId, new Spray(gameTime, sentGlove));
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
        Spray spray = SPRAYS.get(casterId);
        return spray != null && runningAt(spray.lastStreamTick(), now);
    }

    /**
     * The lifted blob's size at a time: beating between its least and
     * greatest size, both above the palm blob's rest size, once a beat.
     *
     * @param time the game time in ticks, with the frame's partial tick
     * @return the size against the palm blob's rest size
     */
    static float sizeAt(double time) {
        double swell = HALF - HALF * Math.cos(FULL_TURN * time / BEAT_TICKS);
        return (float) (LIFTED_LEAST + (LIFTED_GREATEST - LIFTED_LEAST) * swell);
    }

    /**
     * The lifted blob a caster shows this frame: at the caster's spray
     * source in the glove's selected goo type while the spray runs.
     *
     * @param casterId    the caster's entity id
     * @param partialTick the frame's partial tick
     * @return the blob, empty while the caster's spray has ended or the glove has no type selected
     */
    static Optional<Blob> blobOf(int casterId, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Spray spray = SPRAYS.get(casterId);
        if (mc.level == null || spray == null || !runningAt(spray.lastStreamTick(), mc.level.getGameTime())
                || !(mc.level.getEntity(casterId) instanceof Player caster)) {
            return Optional.empty();
        }
        ResourceKey<GooTypeDefinition> type = GooGloveItem.getSelectedType(gloveOf(caster));
        return type == null ? Optional.empty()
                : Optional.of(new Blob(SpraySource.of(casterId, spray.sentGlove(), partialTick), type,
                        sizeAt(mc.level.getGameTime() + (double) partialTick)));
    }

    /**
     * Whether a glove's palm blob stands lifted: a player in the level holds
     * this glove and that player's spray runs.
     * charge-swells-the-palm-blob: the palm blob is the one blob the glove ever shows
     *
     * @param glove the glove being drawn
     * @return true where the palm draws no blob
     */
    public static boolean liftedFrom(ItemStack glove) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SPRAYS.isEmpty()) {
            return false;
        }
        long now = mc.level.getGameTime();
        for (Player player : mc.level.players()) {
            if (gloveOf(player) == glove && running(player.getId(), now)) {
                return true;
            }
        }
        return false;
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
     * Draws every spraying caster's lifted blob at that caster's spray
     * source, and forgets each spray that has ended.
     * palm-blob-draws-as-the-charge-blob: the lifted blob is the charge-style core in its shell
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SPRAYS.isEmpty()) {
            return;
        }
        long now = mc.level.getGameTime();
        SPRAYS.values().removeIf(spray -> !runningAt(spray.lastStreamTick(), now));
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = PortalView.drawing() ? PortalView.camera() : mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Integer casterId : SPRAYS.keySet()) {
            blobOf(casterId, partial).ifPresent(blob -> drawBlob(event.getPoseStack(), buffers, blob,
                    camera.position(), now + partial));
        }
        buffers.endBatch();
    }

    private static void drawBlob(PoseStack pose, MultiBufferSource buffers, Blob blob, Vec3 camera, float time) {
        pose.pushPose();
        pose.translate(blob.place().x - camera.x, blob.place().y - camera.y, blob.place().z - camera.z);
        GooFlightRenderer.renderBlob(pose, buffers, blob.type(), time, blob.size() * FLIGHT_SIZE_OF_PALM_REST);
        pose.popPose();
    }

    /** Forgets every spray, as a level change or a test's end does. */
    static void forgetEverySpray() {
        SPRAYS.clear();
    }
}

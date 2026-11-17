package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mercuriusxeno.goo.network.StackFormedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Knives' formed knives on this client, hovering in an upside-down pyramid
 * over each caster's lifted blob cube as the server counts them: each new
 * knife spawns at the cube's top, growing as it rises to its slot, and the
 * knives already formed stand at theirs. A count of 0 drops the pyramid, so
 * the knives vanish as right click is let go.
 * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-form-on-right-click-and-throw-on-left-click
 */@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HoveringKnives {

    /** Ticks a knife takes to ease from the blob, or from its old slot, to its slot. */
    static final double EASE_TICKS = 6;
    /** The knives a full pyramid holds. */
    private static final int KNIVES = 10;
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    /** Each caster's fan, by entity id. */
    private static final Map<Integer, Fan> FANS = new HashMap<>();

    private HoveringKnives() {
    }

    /**
     * A caster's fan: the knives formed, the count before the last change,
     * and the game time it changed.
     *
     * @param count     the knives formed
     * @param previous  the knives formed before the change
     * @param changedAt the game time of the change
     */
    record Fan(int count, int previous, long changedAt) {
    }

    /**
     * Takes the count the server sent for a caster.
     *
     * @param payload the formed count
     * @param context the network context
     */
    public static void onPayload(StackFormedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                formed(payload.casterId(), payload.formed(), mc.level.getGameTime());
            }
        });
    }

    /**
     * Records a caster's formed count at a game time: 0 drops the fan.
     *
     * @param casterId the caster's entity id
     * @param formed   the knives formed
     * @param now      the game time
     */
    static void formed(int casterId, int formed, long now) {
        if (formed <= 0) {
            FANS.remove(casterId);
            return;
        }
        Fan was = FANS.get(casterId);
        FANS.put(casterId, new Fan(formed, was == null ? 0 : was.count(), now));
    }

    /**
     * A caster's fan.
     *
     * @param casterId the caster's entity id
     * @return the fan, or null where the caster holds no knives
     */
    static @Nullable Fan fanOf(int casterId) {
        return FANS.get(casterId);
    }

    /**
     * The knives in a caster's fan, as the server last counted them.
     *
     * @param casterId the caster's entity id
     * @return the knives, 0 for a caster holding none
     */
    public static int formedBy(int casterId) {
        Fan fan = FANS.get(casterId);
        return fan == null ? 0 : fan.count();
    }

    /** Drops every fan, as a disconnect does. */
    public static void clear() {
        FANS.clear();
    }

    /**
     * Where a knife of the pyramid stands an age after its last change over
     * the cube: at its slot for a knife formed before the change, and for a
     * knife the change formed, rising from the cube's top as it forms to its slot.
     * knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param fan   the pyramid's count
     * @param index the knife's index
     * @param cube  the lifted blob cube's center
     * @param xRot  the caster's view pitch in degrees
     * @param yRot  the caster's view yaw in degrees
     * @param age   ticks since the change, with the partial tick
     * @return the knife's point
     */
    static Vec3 knifeAt(Fan fan, int index, Vec3 cube, float xRot, float yRot, double age) {
        Vec3 slot = StackHover.pointOf(cube, xRot, yRot, StackHover.knifeSlot(index));
        if (index < fan.previous()) {
            return slot;
        }
        Vec3 spawn = StackHover.pointOf(cube, xRot, yRot, StackHover.spawnSlot(index, KNIVES));
        return spawn.lerp(slot, easedAt(age));
    }
    /**
     * A knife's size an age after the fan's last change: whole for a knife
     * formed before it, growing out of the blob for one the change formed.
     *
     * @param fan   the fan
     * @param index the knife's index
     * @param age   ticks since the change, with the partial tick
     * @return the size, 1 at full
     */
    static float knifeScaleAt(Fan fan, int index, double age) {
        return index < fan.previous() ? 1f : easedAt(age);
    }

    static float easedAt(double age) {
        float share = (float) Math.clamp(age / EASE_TICKS, 0.0, 1.0);
        return share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
    }

    /**
     * Draws every caster's fan after translucent blocks, each knife pointing
     * along its caster's look.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || FANS.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE));
        for (Map.Entry<Integer, Fan> entry : FANS.entrySet()) {
            if (mc.level.getEntity(entry.getKey()) instanceof Player caster) {
                drawFan(quads, caster, entry.getValue(), now - entry.getValue().changedAt(), partial, camera);
            }
        }
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
    }

    private static void drawFan(FlatQuadContext quads, Player caster, Fan fan, double age, float partial,
                                Vec3 camera) {
        Vec3 look = caster.getViewVector(partial);
        float xRot = caster.getViewXRot(partial);
        float yRot = caster.getViewYRot(partial);
        Vec3 cube = LiftedPalmBlob.blobOf(caster.getId(), partial).map(LiftedPalmBlob.Blob::place)
                .orElseGet(() -> StackHover.cubeCenterInView(caster.getEyePosition(partial), xRot, yRot,
                        KnifeRainSling.gloveOnTheRight(caster)));
        for (int index = 0; index < fan.count(); index++) {
            GlassKunai.emit(quads, knifeAt(fan, index, cube, xRot, yRot, age).subtract(camera), look, 0f,
                    knifeScaleAt(fan, index, age));
        }
    }
}

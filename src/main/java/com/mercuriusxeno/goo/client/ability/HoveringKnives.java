package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.KnifeFan;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.KnivesFormedPayload;
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
 * Knives' formed knives on this client, hovering in a fan ahead of each
 * caster as the server counts them: each new knife emerges from the inner
 * side of the lifted palm blob, the side toward the view's center, growing
 * as it eases out to its slot, and the knives already formed ease from their
 * old slots to the wider fan's. A count of 0 drops the fan, so the knives
 * vanish as right click is let go.
 * decision knives-emerge-from-the-blob-and-hover-in-a-fan
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-form-on-right-click-and-throw-on-left-click
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HoveringKnives {

    /** Ticks a knife takes to ease from the blob, or from its old slot, to its slot. */
    static final double EASE_TICKS = 6;
    /** Blocks from the blob's center to its inner side, where a knife emerges. */
    static final double BLOB_REACH = 0.08;
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
    public static void onPayload(KnivesFormedPayload payload, IPayloadContext context) {
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
     * Where a knife emerges: the blob's side toward the view's center line,
     * the look's line through the eye.
     *
     * @param blob the blob's center
     * @param eye  the caster's eye
     * @param look the caster's look, a unit vector
     * @return the point on the blob's inner side
     */
    static Vec3 innerSideOf(Vec3 blob, Vec3 eye, Vec3 look) {
        Vec3 nearestOnTheLook = eye.add(look.scale(blob.subtract(eye).dot(look)));
        Vec3 inward = nearestOnTheLook.subtract(blob);
        return inward.lengthSqr() > 0 ? blob.add(inward.normalize().scale(BLOB_REACH)) : blob;
    }

    /**
     * Where a knife of a fan stands an age after the fan's last change: eased
     * from its old slot, or from the blob's inner side for a knife the change
     * formed, to its slot.
     *
     * @param fan    the fan
     * @param index  the knife's index
     * @param eye    the caster's eye
     * @param look   the caster's look, a unit vector
     * @param inner  the blob's inner side
     * @param age    ticks since the change, with the partial tick
     * @return the knife's point
     */
    static Vec3 knifeAt(Fan fan, int index, Vec3 eye, Vec3 look, Vec3 inner, double age) {
        Vec3 slot = KnifeFan.pointOf(eye, look, KnifeFan.slotOf(index, fan.count()));
        Vec3 from = index < fan.previous() ? KnifeFan.pointOf(eye, look, KnifeFan.slotOf(index, fan.previous()))
                : inner;
        return from.lerp(slot, easedAt(age));
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

    private static float easedAt(double age) {
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
        Vec3 eye = caster.getEyePosition(partial);
        Vec3 look = caster.getViewVector(partial);
        Vec3 blob = LiftedPalmBlob.blobOf(caster.getId(), partial).map(LiftedPalmBlob.Blob::place).orElse(eye);
        Vec3 inner = innerSideOf(blob, eye, look);
        for (int index = 0; index < fan.count(); index++) {
            GlassKunai.emit(quads, knifeAt(fan, index, eye, look, inner, age).subtract(camera), look, 0f,
                    knifeScaleAt(fan, index, age));
        }
    }
}

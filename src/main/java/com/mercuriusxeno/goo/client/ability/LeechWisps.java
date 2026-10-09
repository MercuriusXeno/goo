package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.LeechPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Lifetap's look: each leech heal sends a short stream of dark purple wisps
 * from the victim's body to the healed one's chest, the wisps leaving one
 * after another and arching up as they travel, so the life reads as drawn
 * across. Drain's field plays the same wisps from every mob it drains.
 * lifetap-trades-regen-for-leech
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LeechWisps {

    /** Wisps in one leech's stream. */
    static final int WISPS = 6;
    /** Ticks one wisp takes to cross. */
    static final float CROSSING_TICKS = 10f;
    /** Ticks between one wisp's start and the next. */
    static final float STAGGER_TICKS = 1.5f;
    /** How high a wisp arches over the straight line at its midpoint, in blocks. */
    private static final double ARCH = 0.35;
    private static final float WISP_HALF = 0.06f;
    private static final int WISP_RGB = 0x8A3FD0;
    private static final float WISP_ALPHA = 0.9f;
    private static final double BODY_CENTER = 0.5;
    private static final double CHEST = 0.7;
    private static final int OPAQUE = 255;

    private static final List<Leech> LIVE = new ArrayList<>();

    private LeechWisps() {
    }

    /**
     * One leech drawn this frame: its two entities, the game time it began
     * and where the victim stood, held so the stream finishes if it dies.
     *
     * @param victimId the victim's id
     * @param healedId the healed entity's id
     * @param start    the game time it began
     * @param from     the victim's body center when it began
     */
    private record Leech(int victimId, int healedId, long start, Vec3 from) {
    }

    /**
     * Starts a leech's wisps on the client thread.
     *
     * @param payload the leech payload
     * @param context the network context
     */
    public static void handle(LeechPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.victimId()) instanceof Entity victim)) {
                return;
            }
            LIVE.add(new Leech(payload.victimId(), payload.healedId(), mc.level.getGameTime(),
                    victim.position().add(0, victim.getBbHeight() * BODY_CENTER, 0)));
        });
    }

    /**
     * How far along its crossing a wisp is, some ticks into its leech.
     *
     * @param ticks the ticks since the leech began, the partial tick among them
     * @param wisp  the wisp's index in the stream
     * @return 0 to 1 while it crosses; below 0 before it leaves and above 1 once it arrived
     */
    static float crossing(float ticks, int wisp) {
        return (ticks - wisp * STAGGER_TICKS) / CROSSING_TICKS;
    }

    /**
     * Draws every live leech's wisps after the translucent world, dropping
     * each whose last wisp has arrived.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LIVE.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long now = mc.level.getGameTime();
        LIVE.removeIf(leech -> crossing(now - leech.start(), WISPS - 1) > 1f);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = GooRenderTypes.SPORE_SHELL_TYPE;
        VertexConsumer consumer = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        for (Leech leech : LIVE) {
            Entity healed = mc.level.getEntity(leech.healedId());
            if (healed != null) {
                Vec3 to = healed.getPosition(partialTick).add(0, healed.getBbHeight() * CHEST, 0);
                drawStream(pose, consumer, camera, leech.from(), to, now - leech.start() + partialTick);
            }
        }
        buffers.endBatch(type);
    }

    private static void drawStream(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera, Vec3 from, Vec3 to,
                                   float ticks) {
        for (int wisp = 0; wisp < WISPS; wisp++) {
            float along = crossing(ticks, wisp);
            if (along < 0f || along > 1f) {
                continue;
            }
            Vec3 at = from.lerp(to, along).add(0, Math.sin(along * Math.PI) * ARCH, 0).subtract(camera);
            float fade = Mth.sin((float) (along * Math.PI));
            int color = ARGB.color(Math.round(Mth.clamp(WISP_ALPHA * fade, 0f, 1f) * OPAQUE), WISP_RGB);
            SporeMotes.emit(pose, consumer, (float) at.x, (float) at.y, (float) at.z, WISP_HALF, color);
        }
    }
}

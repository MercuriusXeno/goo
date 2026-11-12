package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * Reap's slashes on the client: each a crescent band rippling outward
 * through its level area, drawn from the very slash the server cuts with and
 * placed each frame from where its slasher stands, so it moves with them;
 * its front glows pale and its back shades to Leaf's green, dimming through
 * its life. Other slashers' slashes, and the local player's in third person,
 * stand at the slash's own height under the eye. The local player's own in
 * first person skim the ground at their feet instead, where the eye looks
 * down onto the whole band rather than along its edge. Every slash is depth
 * tested, so the fences, bodies and blocks it passes through hide the part
 * of it behind them and it shows cutting through what it hits.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along a band, tip to tip. */
    private static final int SEGMENTS = 48;
    /** The pale cyan-green its front glows, as the game's slash glows pale at its outer edge. */
    private static final int FRONT_RGB = 0xE0FFF0;
    private static final int OPAQUE = 0xFF;
    /** The back's share of the front's brightness. */
    private static final float BACK_SHARE = 0.45f;
    /** How far over the ground the lowest edge of a first-person slasher's own slash is drawn, in blocks. */
    private static final double GROUND_CLEARANCE = 0.05;
    private static final double BACK = 0;
    private static final double FRONT = 1;

    private static final List<Shown> LIVE = new ArrayList<>();

    private ReapSlashBlades() {
    }

    /**
     * One slash showing.
     *
     * @param slash      the slash
     * @param launchTick the game tick it appeared
     * @param slasherId  the entity id of the one slashing, whom it moves with
     * @param thrownFrom where the slasher's feet stood as it was thrown
     */
    private record Shown(ReapSlash slash, long launchTick, int slasherId, Vec3 thrownFrom) {

        boolean overAt(long tick) {
            return tick - launchTick > slash.lifeTicks();
        }
    }

    /**
     * What one frame draws slashes with.
     *
     * @param mc          the client
     * @param pose        the level's pose
     * @param time        the game time with the partial tick
     * @param partialTick the partial tick
     */
    private record Frame(Minecraft mc, PoseStack.Pose pose, double time, float partialTick) {

        /**
         * Where a shown slash's slasher stands this frame, or where it was
         * thrown from once the client holds no such entity.
         *
         * @param shown the slash
         * @return the slasher's feet
         */
        Vec3 feetOf(Shown shown) {
            Entity slasher = mc.level.getEntity(shown.slasherId());
            return slasher != null ? slasher.getPosition(partialTick) : shown.thrownFrom();
        }

        boolean seenFromItsSlashersEyes(Shown shown) {
            return mc.player != null && mc.player.getId() == shown.slasherId()
                    && mc.options.getCameraType().isFirstPerson();
        }
    }

    /**
     * Starts a slash on the client thread.
     *
     * @param payload the slash
     * @param context the network context
     */
    public static void handle(ReapSlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LIVE.add(new Shown(payload.slash(), mc.level.getGameTime(), payload.slasherId(), payload.thrownFrom()));
            }
        });
    }

    /**
     * Draws every live slash after translucent blocks, dropping each that
     * has dimmed away: the local player's own skimming the ground while
     * they view in first person, every other at its own height.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        long tick = mc.level.getGameTime();
        LIVE.removeIf(shown -> shown.overAt(tick));
        if (LIVE.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Frame frame = new Frame(mc, event.getPoseStack().last(), tick + partialTick, partialTick);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(frame.pose(), buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Shown shown : LIVE) {
            double plane = frame.seenFromItsSlashersEyes(shown)
                    ? shown.slash().skimming(GROUND_CLEARANCE) : shown.slash().height();
            drawSlash(quads, shown, frame, plane, camera, leaf);
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws one slash's band this frame, its front glowing pale and its back
     * Leaf's green, at its strength.
     *
     * @param quads  the context the quads emit through
     * @param shown  the slash
     * @param frame  what the frame draws with
     * @param plane  how high over its slasher's feet its plane is drawn
     * @param camera the camera's position
     * @param leaf   Leaf's green
     */
    private static void drawSlash(FlatQuadContext quads, Shown shown, Frame frame, double plane, Vec3 camera,
            int leaf) {
        ReapSlash slash = shown.slash();
        double age = frame.time() - shown.launchTick();
        float strength = slash.strengthAt(age);
        if (strength <= 0f) {
            return;
        }
        Vec3 from = frame.feetOf(shown).subtract(camera);
        ReapSlash.Band band = slash.shape().bandAt(age);
        int front = ARGB.color(Mth.floor(OPAQUE * strength), FRONT_RGB);
        int back = ARGB.color(Mth.floor(OPAQUE * strength * BACK_SHARE), leaf);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double start = (double) segment / SEGMENTS;
            double end = (double) (segment + 1) / SEGMENTS;
            emit(quads, slash.place(from, plane, band.at(start, FRONT)), front);
            emit(quads, slash.place(from, plane, band.at(end, FRONT)), front);
            emit(quads, slash.place(from, plane, band.at(end, BACK)), back);
            emit(quads, slash.place(from, plane, band.at(start, BACK)), back);
        }
    }

    private static void emit(FlatQuadContext quads, Vec3 point, int color) {
        quads.vertex((float) point.x, (float) point.y, (float) point.z, color);
    }
}

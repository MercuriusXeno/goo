package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Reap's slashes on the client, the Crissaegrim's from Symphony of the Night
 * as its frames show them: each a huge thick crescent drawn whole from the
 * very slash the server cuts with, its bulge glowing pale and its hollow
 * side shading to Leaf's green, thinning and dimming as it travels outward.
 * The slasher's own slashes draw tipped up in first person, so their face
 * shows from the eye as it does from the third-person camera.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along a crescent, tip to tip. */
    private static final int SEGMENTS = 24;
    /** The pale cyan-green its bulge glows, as the game's slash glows pale at its outer edge. */
    private static final int BULGE_RGB = 0xE0FFF0;
    private static final int OPAQUE = 0xFF;
    /** The hollow edge's share of the bulge's brightness. */
    private static final float HOLLOW_SHARE = 0.45f;
    /** How far the slasher's own slashes tip their far edge up in first person, so their face shows as in third person. */
    private static final double FIRST_PERSON_TIP = Math.toRadians(25);
    /** No entity's id, for when no slasher views in first person. */
    private static final int NOBODY = Integer.MIN_VALUE;
    private static final double HOLLOW = 0;
    private static final double BULGE = 1;

    private static final List<Shown> LIVE = new ArrayList<>();

    private ReapSlashBlades() {
    }

    /**
     * One slash showing.
     *
     * @param slash      the slash
     * @param launchTick the game tick it appeared
     * @param slasherId  the entity id of the one slashing
     */
    private record Shown(ReapSlash slash, long launchTick, int slasherId) {

        boolean overAt(long tick) {
            return tick - launchTick > slash.lifeTicks();
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
                LIVE.add(new Shown(payload.slash(), mc.level.getGameTime(), payload.slasherId()));
            }
        });
    }

    /**
     * Draws every live slash after translucent blocks, dropping each that has dimmed away.
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
        double time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        int firstPersonId = mc.player != null && mc.options.getCameraType().isFirstPerson() ? mc.player.getId() : NOBODY;
        for (Shown shown : LIVE) {
            ReapSlash drawn = shown.slasherId() == firstPersonId ? shown.slash().tippedUp(FIRST_PERSON_TIP) : shown.slash();
            drawSlash(quads, drawn, time - shown.launchTick(), camera, leaf);
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws one slash an age into its life, its bulge glowing pale and its
     * hollow edge Leaf's green, dimming as it thins.
     *
     * @param quads  the context the quads emit through
     * @param slash  the slash
     * @param age    ticks since it appeared, with the partial tick
     * @param camera the camera's position
     * @param leaf   Leaf's green
     */
    private static void drawSlash(FlatQuadContext quads, ReapSlash slash, double age, Vec3 camera, int leaf) {
        float strength = slash.strengthAt(age);
        if (strength <= 0f) {
            return;
        }
        int bulge = ARGB.color(Mth.floor(OPAQUE * strength), BULGE_RGB);
        int hollow = ARGB.color(Mth.floor(OPAQUE * strength * HOLLOW_SHARE), leaf);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double from = (double) segment / SEGMENTS;
            double to = (double) (segment + 1) / SEGMENTS;
            emit(quads, slash.pointAt(age, from, BULGE).subtract(camera), bulge);
            emit(quads, slash.pointAt(age, to, BULGE).subtract(camera), bulge);
            emit(quads, slash.pointAt(age, to, HOLLOW).subtract(camera), hollow);
            emit(quads, slash.pointAt(age, from, HOLLOW).subtract(camera), hollow);
        }
    }

    private static void emit(FlatQuadContext quads, Vec3 point, int color) {
        quads.vertex((float) point.x, (float) point.y, (float) point.z, color);
    }
}

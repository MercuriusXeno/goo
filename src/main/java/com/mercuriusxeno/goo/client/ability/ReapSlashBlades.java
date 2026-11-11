package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Camera;
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
 * very slash the server cuts with, its outside edge glowing pale and its
 * inside edge shading to Leaf's green, narrowing and dimming as it travels
 * outward. The slasher's own slashes in first person draw as the
 * third-person camera sees them instead (FirstPersonSweep), shrunk about the
 * eye into the slasher's own body so nothing in the world comes between.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along a crescent, tip to tip. */
    private static final int SEGMENTS = 24;
    /** The pale cyan-green its outside edge glows, as the game's slash glows pale at its outer edge. */
    private static final int BULGE_RGB = 0xE0FFF0;
    private static final int OPAQUE = 0xFF;
    /** The inside edge's share of the outside edge's brightness. */
    private static final float HOLLOW_SHARE = 0.45f;
    /** How far a first-person slash is shrunk about the eye, so its farthest point stays inside the slasher's own body. */
    private static final double VIEW_SCALE = 1.0 / 40;
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
     * @param own        whether the local player threw it
     * @param fan        how far its heading turned off the local player's look as it arrived, for their own
     */
    private record Shown(ReapSlash slash, long launchTick, boolean own, double fan) {

        boolean overAt(long tick) {
            return tick - launchTick > slash.lifeTicks();
        }
    }

    /**
     * The first-person view a slash is held in: the camera's axes.
     *
     * @param forward the camera's unit forward
     * @param right   the camera's unit right
     * @param up      the camera's unit up
     */
    private record Sight(Vec3 forward, Vec3 right, Vec3 up) {

        static Sight of(Camera camera) {
            return new Sight(new Vec3(camera.forwardVector()), new Vec3(camera.leftVector()).reverse(),
                    new Vec3(camera.upVector()));
        }

        /**
         * A view point shrunk about the eye into the space just before it,
         * which the eye sees exactly as it would the full-size point.
         *
         * @param point the point in the view, in blocks
         * @return the point relative to the camera
         */
        Vec3 place(FirstPersonSweep.ViewPoint point) {
            return right.scale(point.right()).add(up.scale(point.up())).add(forward.scale(point.forward()))
                    .scale(VIEW_SCALE);
        }
    }

    /** A point on a slash as drawn this frame, relative to the camera. */
    @FunctionalInterface
    private interface Drawn {
        Vec3 at(double along, double layer);
    }

    /**
     * Starts a slash on the client thread, noting for the local player's own
     * how far its heading turned off their look.
     *
     * @param payload the slash
     * @param context the network context
     */
    public static void handle(ReapSlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                boolean own = mc.player != null && mc.player.getId() == payload.slasherId();
                double fan = own ? payload.slash().turnFrom(mc.player.getViewVector(1f)) : 0;
                LIVE.add(new Shown(payload.slash(), mc.level.getGameTime(), own, fan));
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
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        drawAll(mc, quads, tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                Sight.of(mc.gameRenderer.getMainCamera()));
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws each live slash: the local player's own on their screen while
     * they view in first person, every other in the world.
     *
     * @param mc    the client
     * @param quads the context the quads emit through
     * @param time  the game time with the partial tick
     * @param sight the first-person view
     */
    private static void drawAll(Minecraft mc, FlatQuadContext quads, double time, Sight sight) {
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Shown shown : LIVE) {
            ReapSlash slash = shown.slash();
            double age = time - shown.launchTick();
            Drawn drawn = shown.own() && firstPerson
                    ? (along, layer) -> sight.place(FirstPersonSweep.pointAt(slash, shown.fan(), age, along, layer))
                    : (along, layer) -> slash.pointAt(age, along, layer).subtract(camera);
            drawSlash(quads, drawn, slash.strengthAt(age), leaf);
        }
    }

    /**
     * Draws one slash, its outside edge glowing pale and its inside edge
     * Leaf's green, at its strength.
     *
     * @param quads    the context the quads emit through
     * @param drawn    the slash's points this frame, relative to the camera
     * @param strength its strength, 0 to 1
     * @param leaf     Leaf's green
     */
    private static void drawSlash(FlatQuadContext quads, Drawn drawn, float strength, int leaf) {
        if (strength <= 0f) {
            return;
        }
        int bulge = ARGB.color(Mth.floor(OPAQUE * strength), BULGE_RGB);
        int hollow = ARGB.color(Mth.floor(OPAQUE * strength * HOLLOW_SHARE), leaf);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double from = (double) segment / SEGMENTS;
            double to = (double) (segment + 1) / SEGMENTS;
            emit(quads, drawn.at(from, BULGE), bulge);
            emit(quads, drawn.at(to, BULGE), bulge);
            emit(quads, drawn.at(to, HOLLOW), hollow);
            emit(quads, drawn.at(from, HOLLOW), hollow);
        }
    }

    private static void emit(FlatQuadContext quads, Vec3 point, int color) {
        quads.vertex((float) point.x, (float) point.y, (float) point.z, color);
    }
}

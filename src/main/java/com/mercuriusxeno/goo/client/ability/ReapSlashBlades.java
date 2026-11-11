package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Matrix4fc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reap's slashes on the client, the Crissaegrim's from Symphony of the Night
 * as its frames show them: each a huge thick crescent drawn whole from the
 * very slash the server cuts with, its outside edge glowing pale and its
 * inside edge shading to Leaf's green, narrowing and dimming as it travels
 * outward. The slasher's own slashes in first person draw as FirstPersonSweep
 * places them instead, lying flat under the eye with their outline fitted to
 * the field of view, drawn through every depth test so nothing in the world
 * hides them.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** The quads along a crescent, tip to tip. */
    private static final int SEGMENTS = 24;
    /** The quads along a first-person crescent, more since its arcs span the screen. */
    private static final int OWN_SEGMENTS = 48;
    /** The pale cyan-green its outside edge glows, as the game's slash glows pale at its outer edge. */
    private static final int BULGE_RGB = 0xE0FFF0;
    private static final int OPAQUE = 0xFF;
    /** The inside edge's share of the outside edge's brightness. */
    private static final float HOLLOW_SHARE = 0.45f;
    /** The blocks an eye height of a first-person slash is drawn at, small enough to stand inside the slasher. */
    private static final double BLOCKS_PER_EYE_HEIGHT = 0.25;
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
     * The first-person view a slash is drawn in: the camera's axes and how
     * wide and tall the screen opens.
     *
     * @param forward the camera's unit forward
     * @param right   the camera's unit right
     * @param up      the camera's unit up
     * @param view    how wide and tall the screen opens
     */
    private record Sight(Vec3 forward, Vec3 right, Vec3 up, FirstPersonSweep.View view) {

        static Sight of(Camera camera, Matrix4fc projection) {
            return new Sight(new Vec3(camera.forwardVector()), new Vec3(camera.leftVector()).reverse(),
                    new Vec3(camera.upVector()), new FirstPersonSweep.View(1 / projection.m00(), 1 / projection.m11()));
        }

        /**
         * A view point relative to the camera, shrunk about the eye, which
         * the eye sees exactly as it would the full-size point.
         *
         * @param point the point in the view, in eye heights
         * @return the point relative to the camera, in blocks
         */
        Vec3 place(FirstPersonSweep.ViewPoint point) {
            return right.scale(point.right()).add(up.scale(point.up())).add(forward.scale(point.forward()))
                    .scale(BLOCKS_PER_EYE_HEIGHT);
        }
    }

    /**
     * What one frame draws slashes with.
     *
     * @param mc    the client
     * @param pose  the level's pose
     * @param time  the game time with the partial tick
     * @param sight the first-person view
     */
    private record Frame(Minecraft mc, PoseStack.Pose pose, double time, Sight sight) {
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
     * Draws every live slash after translucent blocks, dropping each that has
     * dimmed away: the local player's own in their first-person view while
     * they view in first person, every other in the world.
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
        Frame frame = new Frame(mc, event.getPoseStack().last(),
                tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false),
                Sight.of(mc.gameRenderer.getMainCamera(), event.getLevelRenderState().cameraRenderState.projectionMatrix));
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Predicate<Shown> inTheView = shown -> shown.own() && firstPerson;
        draw(frame, GooRenderTypes.GLOW_SHELL_TYPE, inTheView.negate(), ReapSlashBlades::inTheWorld, SEGMENTS);
        draw(frame, GooRenderTypes.SCRY_FACES_TYPE, inTheView, ReapSlashBlades::inTheView, OWN_SEGMENTS);
    }

    /** Places a shown slash's points for a frame, an age into its life. */
    @FunctionalInterface
    private interface Placing {
        Drawn of(Frame frame, Shown shown, double age);
    }

    private static Drawn inTheWorld(Frame frame, Shown shown, double age) {
        Vec3 camera = frame.mc().gameRenderer.getMainCamera().position();
        return (along, layer) -> shown.slash().pointAt(age, along, layer).subtract(camera);
    }

    private static Drawn inTheView(Frame frame, Shown shown, double age) {
        Sight sight = frame.sight();
        return (along, layer) -> sight.place(
                FirstPersonSweep.pointAt(shown.slash(), shown.fan(), sight.view(), age, along, layer));
    }

    /**
     * Draws the live slashes one render type takes.
     *
     * @param frame    what the frame draws with
     * @param type     the render type
     * @param taken    which slashes the type takes
     * @param placing  where a slash's points stand
     * @param segments the quads along each crescent
     */
    private static void draw(Frame frame, RenderType type, Predicate<Shown> taken, Placing placing, int segments) {
        if (LIVE.stream().noneMatch(taken)) {
            return;
        }
        MultiBufferSource.BufferSource buffers = frame.mc().renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(frame.pose(), buffers.getBuffer(type));
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Shown shown : LIVE) {
            if (taken.test(shown)) {
                double age = frame.time() - shown.launchTick();
                drawSlash(quads, placing.of(frame, shown, age), shown.slash().strengthAt(age), leaf, segments);
            }
        }
        buffers.endBatch(type);
    }

    /**
     * Draws one slash, its outside edge glowing pale and its inside edge
     * Leaf's green, at its strength.
     *
     * @param quads    the context the quads emit through
     * @param drawn    the slash's points this frame, relative to the camera
     * @param strength its strength, 0 to 1
     * @param leaf     Leaf's green
     * @param segments the quads along the crescent
     */
    private static void drawSlash(FlatQuadContext quads, Drawn drawn, float strength, int leaf, int segments) {
        if (strength <= 0f) {
            return;
        }
        int bulge = ARGB.color(Mth.floor(OPAQUE * strength), BULGE_RGB);
        int hollow = ARGB.color(Mth.floor(OPAQUE * strength * HOLLOW_SHARE), leaf);
        for (int segment = 0; segment < segments; segment++) {
            double from = (double) segment / segments;
            double to = (double) (segment + 1) / segments;
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

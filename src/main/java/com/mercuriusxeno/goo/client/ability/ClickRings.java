package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The rings of Pulse Click's sound wave, drawn in pulse red: one set each
 * client tick of the wave's travel where the wave has reached, each held
 * square to the line from the glove to the landing, swelling a little and
 * fading out where it was set.
 * The rings draw on the path through blocks, after Sculker's dim, so the dim
 * never darkens them: they are sounds, the exception to Sculker's vision, and
 * fullbright, so a ring rippling through stone shows at its own brightness.
 * decision click-is-a-hit-scan-drawn-as-a-delayed-wave
 * decision click-draws-in-pulse-red-not-the-shrieker-visual
 * decision click-rings-are-the-exception-to-sculkers-vision
 * decision submerged-click-ripples-through-the-ground-to-its-range
 */
public final class ClickRings {

    /** The ticks a ring stands, from being set until it has faded out. */
    static final int LIFETIME_TICKS = 10;
    private static final float START_SIZE = 0.35f;
    private static final float SWELL = 0.35f;
    private static final float HALF_TURN = (float) Math.PI;
    /**
     * The pulse red each ring is tinted, the red of Zap's bolt glow and Tick's beat rings.
     * decision click-draws-in-pulse-red-not-the-shrieker-visual
     */
    private static final int RING_RGB = 0xE0301E;
    private static final float OPAQUE = 255f;
    /** The corners of a quad before it is turned and sized, in the order the quad draws them. */
    private static final List<Corner> CORNERS = List.of(new Corner(1, -1, 1, 1), new Corner(1, 1, 1, 0),
            new Corner(-1, 1, 0, 0), new Corner(-1, -1, 0, 1));

    private static final List<Ring> RINGS = new ArrayList<>();

    private ClickRings() {
    }

    /**
     * One ring of the wave.
     *
     * @param at      where the wave had reached when the ring was set
     * @param along   the line from the glove to the landing, which the ring faces along
     * @param setTick the client game tick the ring was set on
     */
    record Ring(Vec3 at, Vec3 along, long setTick) {
    }

    /**
     * One corner of a ring quad before it is turned and sized.
     *
     * @param x the corner's offset across the quad
     * @param y the corner's offset up the quad
     * @param u the texture's horizontal coordinate the corner shows
     * @param v the texture's vertical coordinate the corner shows
     */
    private record Corner(float x, float y, float u, float v) {
    }

    /**
     * Sets a ring where the wave has reached.
     *
     * @param ring the ring
     */
    static void set(Ring ring) {
        RINGS.add(ring);
    }

    /** Drops every ring, as a disconnect does. */
    static void clear() {
        RINGS.clear();
    }

    /**
     * The rings still standing at a tick, dropping the ones that have faded.
     *
     * @param now the client game tick
     * @return the standing rings
     */
    static List<Ring> standingAt(long now) {
        RINGS.removeIf(ring -> now - ring.setTick() >= LIFETIME_TICKS);
        return List.copyOf(RINGS);
    }

    /**
     * The turn that sets a quad square to a line.
     *
     * @param along the line, any length but zero
     * @return the turn from the quad's own facing to the line
     */
    static Quaternionf facingAlong(Vec3 along) {
        if (along.lengthSqr() == 0) {
            return new Quaternionf();
        }
        Vector3f unit = along.normalize().toVector3f();
        return new Quaternionf().rotationTo(0, 0, 1, unit.x, unit.y, unit.z);
    }

    /**
     * A ring quad's corners about its center, in the order the quad draws them.
     *
     * @param facing the quad's turn
     * @param size   half the quad's side
     * @return the four corners
     */
    static List<Vector3f> corners(Quaternionf facing, float size) {
        List<Vector3f> corners = new ArrayList<>(CORNERS.size());
        for (Corner corner : CORNERS) {
            corners.add(new Vector3f(corner.x(), corner.y(), 0).rotate(facing).mul(size));
        }
        return corners;
    }

    /**
     * The color a ring draws in at a share of its life: pulse red, fading
     * from opaque as it is set to nothing as it leaves.
     *
     * @param share the share of the ring's life, 0 as it is set
     * @return the ring's color, ARGB
     */
    static int ringColor(float share) {
        return ARGB.color(Math.round(OPAQUE * (1f - share)), RING_RGB);
    }

    /**
     * Hands the shared path every standing ring, sorted by the nearest one.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the rings' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        long now = mc.level.getGameTime();
        List<Ring> standing = standingAt(now);
        if (standing.isEmpty() || PortalView.drawing()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        draws.accept(SortedDraw.nearestOf(standing.stream().map(Ring::at), camera,
                () -> drawRings(frame.poseStack().last(), standing, camera, now, partialTick)));
    }

    private static void drawRings(PoseStack.Pose pose, List<Ring> standing, Vec3 camera, long now,
                                  float partialTick) {
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.CLICK_WAVE_TYPE);
        for (Ring ring : standing) {
            float share = Math.min(1f, (now - ring.setTick() + partialTick) / LIFETIME_TICKS);
            float size = START_SIZE + SWELL * share;
            int color = ringColor(share);
            Vector3f center = ring.at().subtract(camera).toVector3f();
            Quaternionf facing = facingAlong(ring.along());
            // drawn from both sides, so the ring shows whichever way it is seen from
            emitQuad(pose, consumer, center, corners(facing, size), color);
            emitQuad(pose, consumer, center, corners(new Quaternionf(facing).rotateY(HALF_TURN), size), color);
        }
        buffers.endBatch(GooRenderTypes.CLICK_WAVE_TYPE);
    }

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f center, List<Vector3f> corners,
                                 int color) {
        for (int i = 0; i < CORNERS.size(); i++) {
            Vector3f at = corners.get(i).add(center);
            consumer.addVertex(pose, at.x, at.y, at.z).setUv(CORNERS.get(i).u(), CORNERS.get(i).v()).setColor(color)
                    .setLight(GooSubmitter.fullbrightLight());
        }
    }
}

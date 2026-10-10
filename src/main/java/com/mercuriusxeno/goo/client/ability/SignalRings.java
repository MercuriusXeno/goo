package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PulserToggleStep;
import com.mercuriusxeno.goo.ability.program.SignalWaveStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Signal's and Pulser's ray: while right click holds either stream,
 * concentric red rings facing the aim leave the glove hand one after another
 * and travel out to the stream's range, each expanding as it flies and
 * fading as it expands, like a cartoon space ray: round for Signal, square
 * for Pulser, each sized to its stream's cone.
 * signal-wave-toggles-each-device-once
 * pulser-toggles-rapidly-while-held
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SignalRings {

    /** Rings in flight at once, evenly staggered along the ray. */
    static final int RINGS = 6;
    /** Seconds a ring takes from the hand to the range. */
    static final double FLIGHT_SECONDS = 0.8;
    /** A ring's radius as it leaves the hand, in blocks. */
    static final double HAND_RADIUS = 0.04;
    /** The share of the flight a ring fades in over as it leaves the hand. */
    static final double FADE_IN_SHARE = 0.08;
    /** How many times the window's line width a ring draws at, thick enough to read as a beam. */
    static final float WIDTH_SCALE = 3f;
    private static final int RING_RGB = 0xFF3A2A;
    private static final float PEAK_ALPHA = 230f;
    /** A cone's half angle against its apex angle. */
    private static final double HALF = 0.5;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private SignalRings() {
    }

    /**
     * Draws the rings after the translucent blocks while a signal runs.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility signal = player == null ? null : runningSignal(player);
        if (signal == null || mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Ray ray = new Ray(GloveAim.handPosition(mc.gameRenderer.getMainCamera()), player.getViewVector(partialTick),
                signal.delivery().range(), signal.delivery().coneDegrees(), shapeOf(signal));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        drawRings(lines, ray, mc.gameRenderer.getMainCamera().position(), width,
                System.nanoTime() / NANOS_PER_SECOND);
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    /**
     * The ray the rings fly along this frame.
     *
     * @param hand      where the rings leave, the glove hand
     * @param axis      the aim, unit length
     * @param range       the stream's range in blocks
     * @param coneDegrees the stream's cone, apex to rim, which the rings trace
     * @param shape       the rings' shape, the stream's own
     */
    private record Ray(Vec3 hand, Vec3 axis, double range, double coneDegrees, RingShape shape) {
    }

    private static void drawRings(LineContext lines, Ray ray, Vec3 camera, float width, double seconds) {
        for (int ring = 0; ring < RINGS; ring++) {
            double share = flightShare(seconds, ring);
            int alpha = Math.round(PEAK_ALPHA * opacity(share));
            if (alpha > 0) {
                double distance = share * ray.range();
                Vec3 center = ray.hand().add(ray.axis().scale(distance));
                double radius = radiusAt(share, ray.range(), ray.coneDegrees());
                lines.emitPolyline(camera, ray.shape().points(center, ray.axis(), radius),
                        ARGB.color(alpha, RING_RGB), width);
            }
        }
    }

    /**
     * How far along its flight a ring is: the rings share one clock, each a
     * fixed share of the flight behind the one before.
     *
     * @param seconds seconds on the real-time clock
     * @param ring    the ring's index, 0 to {@link #RINGS} less one
     * @return the share of the flight, from 0 at the hand to just under 1 at the range
     */
    static double flightShare(double seconds, int ring) {
        double share = seconds / FLIGHT_SECONDS + (double) ring / RINGS;
        return share - Math.floor(share);
    }

    /**
     * A ring's opacity along its flight: it fades in quickly as it leaves the
     * hand, then fades steadily as it expands, gone as it reaches the range.
     *
     * @param share the share of the flight
     * @return the opacity, 0 to 1
     */
    static float opacity(double share) {
        return (float) Math.max(0, Math.min(1, Math.min(share / FADE_IN_SHARE, 1 - share)));
    }

    /**
     * A ring's radius along its flight: small at the hand, then the stream
     * cone's own width at the ring's distance, so the rings trace the cone the
     * wave toggles devices in.
     *
     * @param share       the share of the flight
     * @param range       the stream's range in blocks
     * @param coneDegrees the stream's cone, apex to rim, in degrees
     * @return the radius in blocks
     */
    static double radiusAt(double share, double range, double coneDegrees) {
        return HAND_RADIUS + share * range * Math.tan(Math.toRadians(coneDegrees * HALF));
    }

    /**
     * The points of a closed ring facing along an axis.
     *
     * @param center   the ring's center
     * @param axis     the axis the ring faces along, any length
     * @param radius   the ring's radius
     * @param segments the line segments the ring is drawn with
     * @param phase    the turn the first point stands at, as a share of a full turn
     * @return the points, the first repeated last to close the ring
     */
    static Vec3[] ringPoints(Vec3 center, Vec3 axis, double radius, int segments, double phase) {
        Vec3 side = StreamCone.side(axis);
        Vec3 lift = side.cross(axis.normalize());
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double turn = FULL_TURN * ((double) i / segments + phase);
            points[i] = center.add(side.scale(Math.cos(turn) * radius)).add(lift.scale(Math.sin(turn) * radius));
        }
        return points;
    }

    /**
     * The pulse stream the local player's glove runs now.
     *
     * @param player the local player
     * @return the selected ability while right click holds it and it is Signal or Pulser, otherwise null
     */
    static @Nullable ClientAbility runningSignal(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return signals(ability, GloveUseTracker.showsArea()) ? ability : null;
    }

    /**
     * Whether a pulse stream runs: the selected ability sends a signal wave
     * or pulses, and right click holds it.
     *
     * @param ability the selected ability's synced copy, or null when none
     * @param useHeld whether right click holds a live press
     * @return true while the stream runs
     */
    static boolean signals(@Nullable ClientAbility ability, boolean useHeld) {
        return useHeld && ability != null && shapeOf(ability) != null;
    }

    /**
     * The shape a pulse stream's rings draw in: Signal's circles, Pulser's
     * squares, as their icons draw them (decision pulser-toggles-rapidly-while-held).
     *
     * @param ability the selected ability's synced copy
     * @return the shape, or null for an ability that draws no rings
     */
    static @Nullable RingShape shapeOf(ClientAbility ability) {
        if (ability.behaviors().stream().anyMatch(SignalWaveStep.class::isInstance)) {
            return RingShape.CIRCLE;
        }
        return ability.behaviors().stream().anyMatch(PulserToggleStep.class::isInstance) ? RingShape.SQUARE : null;
    }

    /**
     * A ring's outline. A square's sides stand at the ring's radius, so it
     * holds the cone's circle at its distance.
     */
    enum RingShape {
        /** Signal's round rings. */
        CIRCLE(24, 1, 0),
        /** Pulser's square rings, upright to the aim. */
        SQUARE(4, Math.sqrt(2), 0.125);

        private final int segments;
        private final double cornerReach;
        private final double phase;

        RingShape(int segments, double cornerReach, double phase) {
            this.segments = segments;
            this.cornerReach = cornerReach;
            this.phase = phase;
        }

        /**
         * The outline's points around a center, facing along an axis.
         *
         * @param center the ring's center
         * @param axis   the axis the ring faces along
         * @param radius the ring's radius, a square's side standing at it
         * @return the points, the first repeated last to close the outline
         */
        Vec3[] points(Vec3 center, Vec3 axis, double radius) {
            return ringPoints(center, axis, radius * cornerReach, segments, phase);
        }
    }
}

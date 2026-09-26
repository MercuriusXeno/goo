package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Eases the aim arc from what it drew for the last target to the new target
 * over a fixed time on the frame clock, so the line slides rather than snaps
 * (decision aim-line-lerps-toward-target).
 */
final class ArcEndpointEase {
    /** Seconds the arc takes to slide from the last target to the new one. */
    static final double EASE_SECONDS = 0.1;

    private ArcEndpointEase() {}

    /**
     * How far through the ease the arc stands.
     *
     * @param elapsedSeconds seconds since the target changed
     * @param easeSeconds    seconds the whole ease takes
     * @return 0 at the start, 1 at the ease time and after
     */
    static double easeProgress(double elapsedSeconds, double easeSeconds) {
        if (easeSeconds <= 0) {
            return 1;
        }
        return Mth.clamp(elapsedSeconds / easeSeconds, 0, 1);
    }

    /**
     * The endpoint to draw while easing toward a new target.
     *
     * @param fromEndpoint   the endpoint drawn when the target changed, or null when there was none
     * @param toEndpoint     the new target's endpoint
     * @param elapsedSeconds seconds since the target changed
     * @param easeSeconds    seconds the whole ease takes
     * @return a point on the segment between the two endpoints
     */
    static Vec3 easeEndpoint(@Nullable Vec3 fromEndpoint, Vec3 toEndpoint,
            double elapsedSeconds, double easeSeconds) {
        if (fromEndpoint == null) {
            return toEndpoint;
        }
        return fromEndpoint.lerp(toEndpoint, easeProgress(elapsedSeconds, easeSeconds));
    }

    /**
     * The granny weight to draw while easing toward a new target: 0 draws the
     * plain peak, 1 the granny peak.
     *
     * @param fromWeight     the weight drawn when the target changed
     * @param toWeight       the new target's weight
     * @param elapsedSeconds seconds since the target changed
     * @param easeSeconds    seconds the whole ease takes
     * @return a weight between the two
     */
    static double easeGrannyWeight(double fromWeight, double toWeight,
            double elapsedSeconds, double easeSeconds) {
        return Mth.lerp(easeProgress(elapsedSeconds, easeSeconds), fromWeight, toWeight);
    }
}

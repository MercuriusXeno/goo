package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Eases the aim arc from what it drew for the last target to the new target
 * over a fixed time of real time, so the line slides rather than snaps and
 * the tick rate never stretches the slide (decisions
 * aim-line-lerps-toward-target, aim-arc-slides-in-real-time).
 */
final class ArcEndpointEase {
    /** Seconds the arc takes to slide from the last target to the new one. */
    static final double EASE_SECONDS = 0.05;

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
     * Whether a new target restarts the ease. The aimed point moves with the
     * cursor every frame, so the ease restarts only when the favored thing
     * changes, another block, face, entity or kind; a point moving over the
     * same thing carries the ease on, which a restart every frame would hold
     * at its old end.
     * aim-point-follows-the-cursor
     *
     * @param previous the target the arc was easing toward, or null
     * @param next     this frame's target
     * @return true when the ease restarts from what was drawn last frame
     */
    static boolean restartsEase(@Nullable TargetResult previous, TargetResult next) {
        return previous == null || !favoredThing(previous).equals(favoredThing(next));
    }

    /**
     * What a target favors, its point aside.
     *
     * @param target the target
     * @return the block and face, the entity, or the kind for a free point
     */
    private static Object favoredThing(TargetResult target) {
        return switch (target) {
            case TargetResult.BlockTarget bt -> List.of(bt.pos(), bt.face(), bt.grannyArc());
            case TargetResult.EntityTarget et -> et.entity();
            case TargetResult.PointTarget pt -> TargetResult.PointTarget.class;
            default -> target;
        };
    }

    /**
     * Where the aim line ends: the exact point aimed at, so the line slides
     * with the cursor instead of jumping between block centers.
     * aim-point-follows-the-cursor
     *
     * @param target the target this frame aims at
     * @return the line's end, or null when the target names no point
     */
    static @Nullable Vec3 lineEnd(TargetResult target) {
        return target.point();
    }

    /**
     * The granny weight to draw while easing toward a new target: 0 draws the
     * plain peak, 1 the lob peak.
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

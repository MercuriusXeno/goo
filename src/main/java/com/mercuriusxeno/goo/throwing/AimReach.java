package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The one measure of an aimed target's distance, the caster's eye to the
 * aimed point, read by the client's indicator and by every server range
 * gate, so a target the indicator lights is a target the release takes.
 * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
 */
public final class AimReach {

    /**
     * How far past the ability's range the server still takes a release, in
     * blocks: the caster's movement between the frame that lit the target and
     * the packet reaching the server, a sprint of a few ticks.
     */
    public static final double RELEASE_SLACK = 1.0;

    private AimReach() {
    }

    /**
     * Whether the client's indicator lights an aimed point: its distance from
     * where the measure starts is within the range.
     *
     * @param from  where the measure starts, the caster's eye or the partner's point past a gate
     * @param point the aimed point
     * @param range the range left to the measure, in blocks
     * @return true where the indicator lights the point
     */
    public static boolean lights(Vec3 from, Vec3 point, double range) {
        return from.distanceToSqr(point) <= range * range;
    }

    /**
     * Whether the server takes a release aimed this far, within the range and its slack.
     *
     * @param length the aimed point's distance from the eye, through any gate the aim passed
     * @param range  the ability's range in blocks
     * @return true where the release is taken
     */
    public static boolean takes(double length, double range) {
        return length <= range + RELEASE_SLACK;
    }

    /**
     * The aimed point a release claims, held onto the box of the target it
     * names, so a claimed point the target does not hold measures no nearer
     * than the target stands.
     *
     * @param target  the named target's box, the block's cell or the mob's bounding box
     * @param claimed the point the release claims it aimed
     * @return the point of the box nearest the claimed one
     */
    public static Vec3 onTarget(AABB target, Vec3 claimed) {
        return new Vec3(clamp(claimed.x, target.minX, target.maxX), clamp(claimed.y, target.minY, target.maxY),
                clamp(claimed.z, target.minZ, target.maxZ));
    }

    /**
     * How far an aimed point lies from the eye through a gate: the eye to the
     * gate's face, plus the partner's point to the aimed point.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param eye   the caster's eye
     * @param hop   the gate the aim passed
     * @param point the aimed point
     * @return the length in blocks
     */
    public static double lengthThrough(Vec3 eye, GateArc.Hop hop, Vec3 point) {
        return eye.distanceTo(hop.facePoint()) + hop.partnerPoint().distanceTo(point);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

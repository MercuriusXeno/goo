package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A throw's arc split at a gate it passes: the near leg arcs from the hand to
 * the point the aim meets the gate's face, the far leg from the partner's
 * matching point to the target, each by its own length, so the two meet at
 * the gate however the pair is turned. The aim line draws the legs, and a
 * flight flies them.
 * aim-and-shots-carry-through-linked-gates
 */
public final class GateArc {

    private GateArc() {
    }

    /**
     * Where a throw passes a gate: the point on the near gate's face, and the
     * matching point on its partner's.
     *
     * @param facePoint    where the path meets the near gate's face
     * @param partnerPoint where the path comes out of the partner
     */
    public record Hop(Vec3 facePoint, Vec3 partnerPoint) {
    }

    /**
     * The arc's legs, each a polyline arcing by its own peak and meeting the
     * gate at its chord's end, so the near leg lands on the point the aim
     * meets the face and the far leg leaves the partner's matching point,
     * however the pair is turned.
     *
     * @param hand     the arc's start
     * @param hop      the gate it passes, null for a target on the near side
     * @param target   the arc's end
     * @param nearPeak the near leg's peak in blocks, the whole arc's for a near-side target, zero for a beam
     * @param farPeak  the far leg's peak in blocks, zero for a beam
     * @param segments the line segments across the whole arc
     * @return one leg for a near-side target, else the near leg then the far leg
     */
    public static List<Vec3[]> legs(Vec3 hand, @Nullable Hop hop, Vec3 target, double nearPeak, double farPeak,
            int segments) {
        if (hop == null) {
            return Collections.singletonList(ThrowArc.sampleArc(hand, target, nearPeak, segments));
        }
        int nearSegments = Math.max(1, (int) Math.round(segments * nearShare(hand, hop, target)));
        int farSegments = Math.max(1, segments - nearSegments);
        return List.of(ThrowArc.sampleArc(hand, hop.facePoint(), nearPeak, nearSegments),
                ThrowArc.sampleArc(hop.partnerPoint(), target, farPeak, farSegments));
    }

    /**
     * A point along the whole arc: on the near leg's own arc up to the share
     * of the summed length it covers, on the far leg's own arc past it.
     *
     * @param hand     the arc's start
     * @param hop      the gate it passes
     * @param target   the arc's end
     * @param nearPeak the near leg's peak in blocks
     * @param farPeak  the far leg's peak in blocks
     * @param t        how far along the whole arc, from 0 to 1
     * @return the point
     */
    public static Vec3 at(Vec3 hand, Hop hop, Vec3 target, double nearPeak, double farPeak, double t) {
        double split = nearShare(hand, hop, target);
        if (t <= split && split > 0) {
            return ThrowArc.arcPoint(hand, hop.facePoint(), t / split, nearPeak);
        }
        return ThrowArc.arcPoint(hop.partnerPoint(), target, split >= 1 ? 1 : (t - split) / (1 - split), farPeak);
    }

    /**
     * Whether a point along the whole arc stands on the near leg.
     *
     * @param hand   the arc's start
     * @param hop    the gate it passes
     * @param target the arc's end
     * @param t      how far along the whole arc, from 0 to 1
     * @return true up to the gate's face, false past the partner's
     */
    public static boolean onNearLeg(Vec3 hand, Hop hop, Vec3 target, double t) {
        return t <= nearShare(hand, hop, target);
    }

    /**
     * The share of the summed length the near leg covers.
     *
     * @param hand   the arc's start
     * @param hop    the gate it passes
     * @param target the arc's end
     * @return the near leg's share, from 0 to 1
     */
    public static double nearShare(Vec3 hand, Hop hop, Vec3 target) {
        double near = hand.distanceTo(hop.facePoint());
        double total = near + hop.partnerPoint().distanceTo(target);
        return total > 0 ? near / total : 0;
    }
}

package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * A throw's arc split at a gate it passes: the near leg runs from the hand to
 * the point on the gate's face, the far leg from the partner's matching point
 * to the target, and one curve over their summed length lifts both, so the
 * far leg carries on the near leg's curve where it comes out of the partner.
 * The aim line draws the legs, and a flight flies them.
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
     * The arc's legs, each a polyline.
     *
     * @param hand     the arc's start
     * @param hop      the gate it passes, null for a target on the near side
     * @param target   the arc's end
     * @param peak     the arc's peak height in blocks, zero for a beam
     * @param segments the line segments across the whole arc
     * @return one leg for a near-side target, else the near leg then the far leg
     */
    public static List<Vec3[]> legs(Vec3 hand, @Nullable Hop hop, Vec3 target, double peak, int segments) {
        if (hop == null) {
            return Collections.singletonList(ThrowArc.sampleArc(hand, target, peak, segments));
        }
        double split = nearShare(hand, hop, target);
        int nearSegments = Math.max(1, (int) Math.round(segments * split));
        int farSegments = Math.max(1, segments - nearSegments);
        Vec3[] near = new Vec3[nearSegments + 1];
        for (int i = 0; i <= nearSegments; i++) {
            double along = (double) i / nearSegments;
            near[i] = hand.lerp(hop.facePoint(), along).add(0, lift(split * along, peak), 0);
        }
        Vec3[] far = new Vec3[farSegments + 1];
        for (int i = 0; i <= farSegments; i++) {
            double along = (double) i / farSegments;
            far[i] = hop.partnerPoint().lerp(target, along).add(0, lift(split + (1 - split) * along, peak), 0);
        }
        return List.of(near, far);
    }

    /**
     * A point along the whole arc: on the near leg up to the share of the
     * summed length it covers, on the far leg past it, raised above its leg's
     * chord by the one curve both legs share.
     *
     * @param hand   the arc's start
     * @param hop    the gate it passes
     * @param target the arc's end
     * @param peak   the arc's peak height in blocks
     * @param t      how far along the whole arc, from 0 to 1
     * @return the point
     */
    public static Vec3 at(Vec3 hand, Hop hop, Vec3 target, double peak, double t) {
        double split = nearShare(hand, hop, target);
        Vec3 onChord = t <= split && split > 0 ? hand.lerp(hop.facePoint(), t / split)
                : hop.partnerPoint().lerp(target, split >= 1 ? 1 : (t - split) / (1 - split));
        return onChord.add(0, lift(t, peak), 0);
    }

    /**
     * The straight pieces a beam draws between its tail and its head, each
     * a start and an end: one where both stand on one leg, two where the
     * beam spans the gate, from the tail to the face and from the partner
     * to the head.
     *
     * @param hand   the beam's start
     * @param hop    the gate it passes
     * @param target the beam's end
     * @param tail   how far along the whole path the tail stands, from 0 to 1
     * @param head   how far along the whole path the head stands, from 0 to 1
     * @return the pieces
     */
    public static List<Vec3[]> beamPieces(Vec3 hand, Hop hop, Vec3 target, double tail, double head) {
        Vec3 from = at(hand, hop, target, 0, tail);
        Vec3 to = at(hand, hop, target, 0, head);
        if (onNearLeg(hand, hop, target, tail) == onNearLeg(hand, hop, target, head)) {
            return Collections.singletonList(new Vec3[] {from, to});
        }
        return List.of(new Vec3[] {from, hop.facePoint()}, new Vec3[] {hop.partnerPoint(), to});
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

    private static double lift(double t, double peak) {
        return ThrowArc.skewedArc(t, peak, ThrowArc.ARC_PEAK_T);
    }
}

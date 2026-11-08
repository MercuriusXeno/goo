package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.throwing.GateArc;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A ray carried through linked gates: a ray that meets a linked gate's face
 * going in, within its oval, carries on out of the partner, turned through
 * the pair, for the reach it has left. Portals and holes both stand in the
 * pairs, so a ray passes a tunnel as it passes a portal; an inert portal
 * stands in no pair, so a ray stops on it as on any surface.
 * aim-and-shots-carry-through-linked-gates
 */
public final class GateRay {

    private GateRay() {
    }

    /**
     * Where a ray passes through a gate.
     *
     * @param near      the gate the ray goes into
     * @param far       its partner, the ray comes out of
     * @param facePoint where the ray meets the near gate's face
     * @param farFrom   where the ray carries on from, just out of the partner's face
     * @param farTo     where the ray ends past the partner, its remaining reach on
     */
    public record Crossing(Gate near, Gate far, Vec3 facePoint, Vec3 farFrom, Vec3 farTo) {
    }

    /**
     * The first linked gate a ray passes through.
     *
     * @param pairs     the linked pairs
     * @param dimension the level the ray runs in
     * @param from      the ray's start
     * @param to        the ray's end
     * @return where it passes through, empty where it meets no linked gate's face within its oval
     */
    public static Optional<Crossing> first(List<GatePassage.Pair> pairs, ResourceKey<Level> dimension, Vec3 from,
            Vec3 to) {
        Optional<Crossing> nearest = Optional.empty();
        double nearestAlong = Double.MAX_VALUE;
        for (GatePassage.Pair pair : pairs) {
            for (GatePassage.Pair way : List.of(pair, new GatePassage.Pair(pair.other(), pair.one()))) {
                OptionalDouble along = meets(way.one(), dimension, from, to);
                if (along.isPresent() && along.getAsDouble() < nearestAlong) {
                    nearestAlong = along.getAsDouble();
                    nearest = Optional.of(crossingAt(way.one(), way.other(), from, to, nearestAlong));
                }
            }
        }
        return nearest;
    }

    /**
     * The partner a gate is linked to among the pairs.
     *
     * @param pairs the linked pairs
     * @param gate  the gate
     * @return its partner, empty where it stands in no pair
     */
    public static Optional<Gate> partnerIn(List<GatePassage.Pair> pairs, Gate gate) {
        for (GatePassage.Pair pair : pairs) {
            if (pair.one().equals(gate)) {
                return Optional.of(pair.other());
            }
            if (pair.other().equals(gate)) {
                return Optional.of(pair.one());
            }
        }
        return Optional.empty();
    }

    /**
     * The hop a path makes through a gate: its point on the gate's face, and
     * the partner's matching point.
     *
     * @param pairs     the linked pairs
     * @param gate      the gate the path goes into
     * @param facePoint where it meets the gate's face
     * @return the hop, empty where the gate stands in no pair
     */
    public static Optional<GateArc.Hop> hopThrough(List<GatePassage.Pair> pairs, Gate gate, Vec3 facePoint) {
        return partnerIn(pairs, gate).map(far -> new GateArc.Hop(facePoint,
                PortalTransform.between(gate, far).point(facePoint)));
    }

    /**
     * How far along a ray, as a fraction of it, the ray meets a gate's face
     * going in, within its oval.
     *
     * @param gate      the gate
     * @param dimension the level the ray runs in
     * @param from      the ray's start
     * @param to        the ray's end
     * @return the fraction, empty where the ray meets the face from behind, outside the oval or not at all
     */
    private static OptionalDouble meets(Gate gate, ResourceKey<Level> dimension, Vec3 from, Vec3 to) {
        double before = gate.out(from);
        double after = gate.out(to);
        if (!gate.dimension().equals(dimension) || before < 0 || after >= 0) {
            return OptionalDouble.empty();
        }
        double along = before / (before - after);
        return gate.withinOval(from.lerp(to, along)) ? OptionalDouble.of(along) : OptionalDouble.empty();
    }

    /**
     * The ray carried on out of the partner: from the partner's matching
     * point, set just out of its face so a clip starts clear of the
     * partner's own blocks, turned through the pair, for the reach left.
     *
     * @param near  the gate the ray goes into
     * @param far   its partner
     * @param from  the ray's start
     * @param to    the ray's end
     * @param along how far along the ray, as a fraction of it, it meets the face
     * @return the crossing
     */
    private static Crossing crossingAt(Gate near, Gate far, Vec3 from, Vec3 to, double along) {
        Vec3 facePoint = from.lerp(to, along);
        PortalTransform through = PortalTransform.between(near, far);
        Vec3 onward = through.direction(to.subtract(from).normalize());
        Vec3 farFrom = through.point(facePoint).add(far.face().getUnitVec3().scale(GatePassage.LANDING_MARGIN));
        double left = from.distanceTo(to) * (1 - along) - GatePassage.LANDING_MARGIN;
        return new Crossing(near, far, facePoint, farFrom, farFrom.add(onward.scale(Math.max(left, 0))));
    }
}

package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ray meeting a linked gate's face within its oval carries on out of the
 * partner, turned through the pair, for the reach it has left; one meeting
 * the face outside the oval, or a gate in no pair, carries on nowhere
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class GateRayTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 portal on a wall's north face, the face the plane z 0. */
    private static final Gate NEAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 0)).minmax(
            new AABB(new BlockPos(1, 2, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on a wall's south face, the plane z 3. */
    private static final Gate FAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 2)).minmax(
            new AABB(new BlockPos(1, 2, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    /** A hole's mouth on a floor, the plane y 65, as Wormhole lays one. */
    private static final Gate FLOOR_MOUTH = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 0)),
            Direction.UP, Direction.NORTH.getUnitVec3());
    /** Its far mouth on the ceiling of a cell below, the plane y 60. */
    private static final Gate CEILING_MOUTH = new Gate(OVERWORLD, new AABB(new BlockPos(0, 60, 0)),
            Direction.DOWN, Direction.NORTH.getUnitVec3());
    private static final double CLOSE = 1.0E-3;

    @Test
    void aRayIntoAWallPairCarriesOnOutOfThePartner() {
        Vec3 from = new Vec3(0.5, 1.5, -5.0);
        Vec3 to = new Vec3(0.5, 1.5, 5.0);
        GateRay.Crossing crossing = GateRay.first(List.of(new GatePassage.Pair(NEAR, FAR)), OVERWORLD, from, to)
                .orElseThrow(() -> new AssertionError("A ray into a linked wall portal should pass through it"));
        assertContinues(crossing, NEAR, FAR, new Vec3(0.5, 1.5, 0.0), from, to);
    }

    @Test
    void aRayThroughAHolePairCarriesOnOutOfTheFarMouth() {
        Vec3 from = new Vec3(0.5, 70.0, 0.5);
        Vec3 to = new Vec3(0.5, 50.0, 0.5);
        GateRay.Crossing crossing = GateRay.first(List.of(new GatePassage.Pair(FLOOR_MOUTH, CEILING_MOUTH)),
                OVERWORLD, from, to)
                .orElseThrow(() -> new AssertionError("A ray down into a hole's mouth should pass through it"));
        assertContinues(crossing, FLOOR_MOUTH, CEILING_MOUTH, new Vec3(0.5, 65.0, 0.5), from, to);
    }

    @Test
    void aRayMeetingTheFaceOutsideTheOvalPassesNoGate() {
        Vec3 from = new Vec3(-0.9, 0.1, -5.0);
        Vec3 to = new Vec3(-0.9, 0.1, 5.0);
        assertEquals(Optional.empty(), GateRay.first(List.of(new GatePassage.Pair(NEAR, FAR)), OVERWORLD, from, to),
                "A ray meeting the gate's blocks outside its oval should stop there as a surface");
    }

    @Test
    void aGateInNoPairPassesNoRay() {
        Vec3 from = new Vec3(0.5, 1.5, -5.0);
        Vec3 to = new Vec3(0.5, 1.5, 5.0);
        assertEquals(Optional.empty(), GateRay.first(List.of(new GatePassage.Pair(FLOOR_MOUTH, CEILING_MOUTH)),
                OVERWORLD, from, to), "A ray into a gate that stands in no pair, an inert portal, should stop there");
    }

    /**
     * A held body on a pair's far side reaches the holder through the pair:
     * it heads for the middle of the far gate's opening, past its face, so it
     * fits through and the pair carries it out to the holder's side
     * (decision aim-and-shots-carry-through-linked-gates).
     */
    @Test
    void aBodyPastAPairHeadsThroughTheMiddleOfItsOpening() {
        Vec3 held = new Vec3(0.5, 1.5, 7.0);
        Vec3 holdPoint = new Vec3(0.5, 1.5, -3.0);
        GateRay.Route route = GateRay.shortestRoute(List.of(new GatePassage.Pair(NEAR, FAR)), held, holdPoint)
                .orElseThrow(() -> new AssertionError("A body before the far gate should reach the hold point through "
                        + "the pair"));
        assertEquals(FAR, route.in(), "The body should go into the gate on its own side");
        Vec3 heading = route.headFor();
        assertTrue(FAR.out(heading) < 0, "The body should head past the far gate's face, heads for " + heading);
        assertTrue(FAR.withinOval(heading.add(FAR.face().getUnitVec3().scale(-FAR.out(heading)))),
                "The body should head through the middle of the gate's opening, heads for " + heading);
        assertEquals(held.distanceTo(FAR.center()) + NEAR.center().distanceTo(holdPoint), route.length(), CLOSE,
                "The way should measure both legs");
    }

    @Test
    void noPairNoRoute() {
        assertEquals(Optional.empty(), GateRay.shortestRoute(List.of(), new Vec3(0.5, 1.5, 7.0),
                new Vec3(0.5, 1.5, -3.0)), "With no pair there should be no way through one");
    }

    private static void assertContinues(GateRay.Crossing crossing, Gate near, Gate far, Vec3 facePoint, Vec3 from,
            Vec3 to) {
        assertEquals(near, crossing.near(), "the gate the ray went into");
        assertEquals(far, crossing.far(), "the partner the ray comes out of");
        assertTrue(crossing.facePoint().distanceTo(facePoint) < CLOSE, "The ray should meet the face at "
                + facePoint + ", met it at " + crossing.facePoint());
        PortalTransform through = PortalTransform.between(near, far);
        assertTrue(crossing.farFrom().distanceTo(through.point(facePoint)) < GatePassage.LANDING_MARGIN + CLOSE,
                "The ray should carry on from the partner's matching point " + through.point(facePoint)
                        + ", carries on from " + crossing.farFrom());
        Vec3 onward = crossing.farTo().subtract(crossing.farFrom());
        Vec3 turned = through.direction(to.subtract(from).normalize());
        assertTrue(onward.normalize().distanceTo(turned) < CLOSE, "The ray should carry on turned through the pair, "
                + turned + ", carries on " + onward.normalize());
        double left = to.distanceTo(from) - from.distanceTo(facePoint);
        assertTrue(Math.abs(onward.length() - left) < GatePassage.LANDING_MARGIN + CLOSE, "The ray should carry on "
                + "for the reach it has left, " + left + ", carries on " + onward.length());
    }
}

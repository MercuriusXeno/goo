package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream's cone keeps a point on its axis within range and drops one
 * outside its half-angle, beyond its range or behind its apex
 * (decision stream-delivery-held-cone).
 */
class StreamConeTest {

    private static final Vec3 APEX = Vec3.ZERO;
    private static final Vec3 AXIS = new Vec3(1, 0, 0);
    private static final double RANGE = 6;
    private static final double CONE = 20;

    private static boolean contains(Vec3 point) {
        return StreamCone.contains(APEX, AXIS, RANGE, CONE, point);
    }

    @Test
    void pointOnTheAxisWithinRangeIsKept() {
        assertTrue(contains(new Vec3(4, 0, 0)));
    }

    @Test
    void pointOutsideTheHalfAngleIsDropped() {
        assertFalse(contains(new Vec3(4, 2, 0)));
    }

    @Test
    void pointBeyondRangeIsDropped() {
        assertFalse(contains(new Vec3(7, 0, 0)));
    }

    @Test
    void pointJustInsideTheHalfAngleIsKept() {
        double justInside = Math.tan(Math.toRadians(CONE / 2)) * 4 * 0.99;
        assertTrue(contains(new Vec3(4, justInside, 0)));
    }

    @Test
    void pointJustOutsideTheHalfAngleIsDropped() {
        double justOutside = Math.tan(Math.toRadians(CONE / 2)) * 4 * 1.01;
        assertFalse(contains(new Vec3(4, 0, justOutside)));
    }

    @Test
    void pointBehindTheApexIsDropped() {
        assertFalse(contains(new Vec3(-4, 0, 0)));
    }

    @Test
    void axisLengthDoesNotMatter() {
        assertTrue(StreamCone.contains(APEX, AXIS.scale(5), RANGE, CONE, new Vec3(4, 0.5, 0)));
    }

    @Test
    void aLaunchOnTheAxisFliesAlongIt() {
        Vec3 heading = StreamCone.launchDirection(AXIS, CONE, 0, 0.3);
        assertTrue(heading.subtract(AXIS).length() < 1e-9, heading.toString());
    }

    @Test
    void everyLaunchStaysInsideTheConeAndTheRimReachesItsEdge() {
        for (double turn = 0; turn < 1; turn += 0.125) {
            for (Vec3 axis : new Vec3[] {AXIS, new Vec3(0, -1, 0), new Vec3(1, 2, -3)}) {
                Vec3 rim = StreamCone.launchDirection(axis, CONE, 1, turn);
                double degreesOff = Math.toDegrees(Math.acos(rim.dot(axis.normalize())));
                assertTrue(Math.abs(degreesOff - CONE / 2) < 1e-6, axis + " " + turn + " " + degreesOff);
                assertTrue(StreamCone.contains(APEX, axis, RANGE, CONE + 1e-6, rim.scale(RANGE / 2)));
            }
        }
    }

    @Test
    void launchesAroundTheAxisFanOutInDifferentDirections() {
        Vec3 east = StreamCone.launchDirection(AXIS, CONE, 1, 0);
        Vec3 opposite = StreamCone.launchDirection(AXIS, CONE, 1, 0.5);
        assertFalse(east.subtract(opposite).length() < 0.1, east + " " + opposite);
    }
}

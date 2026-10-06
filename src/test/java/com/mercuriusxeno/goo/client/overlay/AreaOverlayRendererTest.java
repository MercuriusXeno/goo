package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityArea;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held area overlay builds the shape and size an ability's area block
 * names, and an ability with no area draws nothing beside its reticule
 * (decision right-click-held-previews-release-throws).
 */
class AreaOverlayRendererTest {

    private static final double TOLERANCE = 1e-9;
    private static final Vec3 HAND = new Vec3(0.3, 65.4, 0.2);
    private static final Vec3 POINT = new Vec3(6.5, 64.0, -9.5);
    private static final int SPHERE_CIRCLES = 3;

    @Test
    void aSphereRingsThePointAtItsRadius() {
        double radius = 3.0;

        List<Vec3[]> lines = AreaOverlayRenderer.areaLines(new AbilityArea(AbilityArea.Shape.SPHERE, radius, 0),
                HAND, POINT);

        assertEquals(SPHERE_CIRCLES, lines.size());
        assertAll(lines.stream().flatMap(Arrays::stream)
                .map(vertex -> () -> assertEquals(radius, vertex.distanceTo(POINT), TOLERANCE)));
    }

    @Test
    void aLineRunsFromTheHandToThePoint() {
        List<Vec3[]> lines = AreaOverlayRenderer.areaLines(new AbilityArea(AbilityArea.Shape.LINE, 0, 0), HAND, POINT);

        assertEquals(1, lines.size());
        assertArrayEquals(new Vec3[] {HAND, POINT}, lines.get(0));
    }

    @Test
    void aConeOpensFromTheHandTowardThePointAtItsLengthAndAngle() {
        double length = 6.0;
        double halfAngle = 20.0;
        Vec3 axis = POINT.subtract(HAND).normalize();
        double baseRadius = length * Math.tan(Math.toRadians(halfAngle));

        List<Vec3[]> lines = AreaOverlayRenderer.areaLines(
                new AbilityArea(AbilityArea.Shape.CONE, length, halfAngle), HAND, POINT);

        Vec3 baseCenter = HAND.add(axis.scale(length));
        assertEquals(1 + AreaOverlayRenderer.CONE_EDGES, lines.size());
        assertAll(Arrays.stream(lines.get(0)).map(rim -> () -> {
            assertEquals(baseRadius, rim.distanceTo(baseCenter), TOLERANCE);
            assertEquals(0, rim.subtract(baseCenter).dot(axis), TOLERANCE);
        }));
        assertAll(lines.subList(1, lines.size()).stream().map(edge -> () -> {
            assertEquals(HAND, edge[0]);
            assertEquals(baseRadius, edge[1].distanceTo(baseCenter), TOLERANCE);
        }));
    }

    @Test
    void anAbilityWithNoAreaDrawsNothing() {
        assertTrue(AreaOverlayRenderer.areaLines(AbilityArea.NONE, HAND, POINT).isEmpty());
    }
}

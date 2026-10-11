package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glass knife on the client flies the server's arc tick by tick to the
 * end the server found, point first along its path, its kunai laid square
 * to its heading (decision shards-sling-then-morph-to-flechettes).
 */
class GlassKnivesTest {

    private static final double TOLERANCE = 1e-9;
    private static final Vec3 START = new Vec3(0, 2, 0);
    private static final Vec3 VELOCITY = new Vec3(0, 0.2, 1);

    @Test
    void theClientPathFollowsTheServersArcAndEndsWhereTheServerSaid() {
        Vec3 end = new Vec3(0.1, 0.5, 6.2);
        List<Vec3> path = GlassKnives.pathOf(START, VELOCITY, 7, end);

        assertEquals(8, path.size());
        assertEquals(START, path.getFirst());
        assertEquals(0, KnifeRain.positionAt(START, VELOCITY, 6).distanceTo(path.get(6)), TOLERANCE);
        assertEquals(end, path.getLast());
    }

    @Test
    void aKnifeLiesBetweenItsTickPointsHeadingAlongItsPath() {
        List<Vec3> path = GlassKnives.pathOf(START, VELOCITY, 12, KnifeRain.positionAt(START, VELOCITY, 12));

        GlassKnives.Placement half = GlassKnives.placementAt(path, 1.5);
        assertEquals(0, path.get(1).lerp(path.get(2), 0.5).distanceTo(half.point()), TOLERANCE);
        assertEquals(0, path.get(2).subtract(path.get(1)).normalize().distanceTo(half.heading()), TOLERANCE);
        assertEquals(path.getLast(), GlassKnives.placementAt(path, 99).point());
        assertTrue(GlassKnives.placementAt(path, 11.5).heading().y < 0, "the knife points down as it falls");
    }

    @Test
    void aKunaisFrameLiesSquareToItsHeading() {
        Vec3 heading = new Vec3(0.3, -0.4, 0.8).normalize();
        Vec3[] frame = GlassKunai.frame(heading, 0.7f);

        assertEquals(0, frame[0].dot(heading), TOLERANCE);
        assertEquals(0, frame[1].dot(heading), TOLERANCE);
        assertEquals(0, frame[0].dot(frame[1]), TOLERANCE);
        assertEquals(1, frame[0].length(), TOLERANCE);
        assertEquals(0, GlassKunai.frame(new Vec3(0, 0, 1), 0f)[0].y, TOLERANCE);
    }
}

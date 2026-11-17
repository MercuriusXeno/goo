package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GlassKnifePayload;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glass knife on the client homes on its mob tick by tick, ending on its
 * mob, in a block it meets, or spent where its gone mob last stood, point
 * first along its path, its kunai laid square to its heading (decisions
 * knives-sling-then-morph-to-flechettes, knives-home-on-their-painted-mob).
 */
class GlassKnivesTest {

    private static final double TOLERANCE = 1e-9;
    private static final AABB MOB = new AABB(-0.3, 0, 4.7, 0.3, 1.8, 5.3);
    private static final Vec3 LAST_KNOWN = new Vec3(0, 0.9, 5);

    @Test
    void aStepReachingTheMobsBoxStrikesIt() {
        assertEquals(Optional.of(GlassKnifePayload.Ending.STRUCK), GlassKnives.endingOf(new Vec3(0, 0.9, 3.8),
                new Vec3(0, 0.9, 5.3), MOB, LAST_KNOWN, false, 3));
    }

    @Test
    void aStepMeetingABlockSticks() {
        assertEquals(Optional.of(GlassKnifePayload.Ending.STUCK), GlassKnives.endingOf(Vec3.ZERO,
                new Vec3(0, 0, 1.5), MOB, LAST_KNOWN, true, 1));
    }

    @Test
    void aKnifeWhoseMobIsGoneShattersWhereTheMobLastStood() {
        assertEquals(Optional.empty(), GlassKnives.endingOf(Vec3.ZERO, new Vec3(0, 0.27, 1.47), null, LAST_KNOWN,
                false, 1), "short of the last place it flies on");
        assertEquals(Optional.of(GlassKnifePayload.Ending.SPENT), GlassKnives.endingOf(new Vec3(0, 0.9, 4),
                LAST_KNOWN, null, LAST_KNOWN, false, 4));
    }

    @Test
    void aKnifeLiesBetweenItsTickPointsHeadingAlongItsPath() {
        List<Vec3> path = List.of(Vec3.ZERO, new Vec3(0, 0, 1.5), new Vec3(0.3, 0, 3));

        GlassKnives.Placement half = GlassKnives.placementAt(path, 1.5);
        assertEquals(0, path.get(1).lerp(path.get(2), 0.5).distanceTo(half.point()), TOLERANCE);
        assertEquals(0, path.get(2).subtract(path.get(1)).normalize().distanceTo(half.heading()), TOLERANCE);
        assertEquals(path.getLast(), GlassKnives.placementAt(path, 99).point());
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

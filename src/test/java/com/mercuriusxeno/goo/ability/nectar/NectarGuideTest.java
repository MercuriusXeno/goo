package com.mercuriusxeno.goo.ability.nectar;

import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which mob Nectar's drips are guided to: one whose middle lies
 * within the guide radius of the look ray, inside the guide reach, and none
 * where no mob lies that near, so the spray goes free along the look vector.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 */
class NectarGuideTest {

    private static final Vec3 EYE = Vec3.ZERO;
    private static final Vec3 LOOK = new Vec3(1, 0, 0);
    private static final double GUIDE_RADIUS = 2;
    private static final double GUIDE_REACH = 16;
    private static final int COW = 7;
    private static final int PIG = 9;
    /** jelly_nectar.json's throw speed, a caster at rest. */
    private static final double EXIT_SPEED = 0.84;
    private static final double ON_THE_MOB = 0.3;
    private static final int FLIGHT_TICKS = 60;
    private static final double SEGMENT_SHARE = 0.05;

    @Test
    void anAimPassingNearAMobIsGuidedToIt() {
        List<NectarGuide.Candidate> mobs = List.of(new NectarGuide.Candidate(COW, new Vec3(8, 1.5, 0)));

        assertEquals(Optional.of(COW), NectarGuide.guidedTo(EYE, LOOK, mobs, GUIDE_RADIUS, GUIDE_REACH));
    }

    @Test
    void anAimPassingNoMobWithinTheRadiusSpraysFree() {
        List<NectarGuide.Candidate> mobs = List.of(new NectarGuide.Candidate(COW, new Vec3(8, 2.5, 0)));

        assertEquals(Optional.empty(), NectarGuide.guidedTo(EYE, LOOK, mobs, GUIDE_RADIUS, GUIDE_REACH));
    }

    @Test
    void aMobPastTheReachOrBehindTheEyeGuidesNothing() {
        List<NectarGuide.Candidate> mobs = List.of(new NectarGuide.Candidate(COW, new Vec3(17, 0, 0)),
                new NectarGuide.Candidate(PIG, new Vec3(-4, 0, 0)));

        assertEquals(Optional.empty(), NectarGuide.guidedTo(EYE, LOOK, mobs, GUIDE_RADIUS, GUIDE_REACH));
    }

    @Test
    void theMobNearestTheLookTakesTheGuide() {
        List<NectarGuide.Candidate> mobs = List.of(new NectarGuide.Candidate(COW, new Vec3(4, 1.5, 0)),
                new NectarGuide.Candidate(PIG, new Vec3(12, 0.5, 0)));

        assertEquals(Optional.of(PIG), NectarGuide.guidedTo(EYE, LOOK, mobs, GUIDE_RADIUS, GUIDE_REACH));
    }

    @Test
    void aThrowOntoAMobFliesItsDripsArcThroughTheMob() {
        Vec3 apex = new Vec3(0, 1.5, 0);
        Vec3 mob = new Vec3(9, 0, 4);

        Vec3 axis = NectarGuide.throwOnto(apex, mob, EXIT_SPEED, DripArc.GRAVITY);

        assertTrue(nearestPass(apex, axis.scale(EXIT_SPEED), mob) <= ON_THE_MOB);
    }

    /** How near a drip's arc, flown by DripArc through open air, passes a point, checked at each tick's end. */
    private static double nearestPass(Vec3 apex, Vec3 launched, Vec3 point) {
        Vec3 position = apex;
        Vec3 velocity = launched;
        double nearest = position.distanceTo(point);
        for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
            DripArc.Step step = DripArc.step(position, velocity, (from, to) -> Optional.empty(), at -> true);
            for (double share = 0; share <= 1; share += SEGMENT_SHARE) {
                nearest = Math.min(nearest, position.add(step.position().subtract(position).scale(share))
                        .distanceTo(point));
            }
            position = step.position();
            velocity = step.velocity();
        }
        return nearest;
    }
}

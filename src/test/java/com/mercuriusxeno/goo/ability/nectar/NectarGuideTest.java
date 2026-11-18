package com.mercuriusxeno.goo.ability.nectar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

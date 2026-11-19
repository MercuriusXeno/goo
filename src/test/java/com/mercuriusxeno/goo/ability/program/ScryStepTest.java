package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScryStep's front: the distances one tick's front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryStepTest {

    private static final double REACH = 96;
    private static final double ONE_TICK = 2.4;
    private static final double WALKED = 20;

    /** Scry pings as each pulse leaves the caster, on the pulse's first tick alone (operator ruling 2026-11-06). */
    @Test
    void scryPingsOnEachPulsesFirstTickAlone() {
        HeldSweepPulses.Pulse pulse = new HeldSweepPulses.Pulse(1, net.minecraft.world.phys.Vec3.ZERO, 100);

        assertTrue(ScryStep.pingsOn(pulse, 100), "as the pulse leaves");
        assertFalse(ScryStep.pingsOn(pulse, 101), "not as it travels");
    }

    /**
     * One Scry strike's work over the fixed synthetic world: the entities
     * its scan visits on the front's last tick out to its reach, from a
     * caster standing at the pulse's origin and from one walked away from
     * it, since the scan reaches the front past the caster's walk.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     */
    @Test
    void oneStrikeAtScrysReachVisitsEveryMobTheFrontAndTheCastersWalkReach() {
        HeldSweepWorld world = new HeldSweepWorld();
        Vec3 origin = HeldSweepWorld.CASTER;
        Vec3 walked = origin.add(WALKED, 0, 0);
        HeldSweepWorld.CountingScan still = world.scanAround(origin);
        HeldSweepWorld.CountingScan away = world.scanAround(walked);
        int[] struck = new int[2];

        ScryStep.strikeCrossed(new ScryStep.Strike(still, Set.of(), origin, origin), REACH - ONE_TICK, REACH,
                selected -> struck[0]++);
        ScryStep.strikeCrossed(new ScryStep.Strike(away, Set.of(), walked, origin), REACH - ONE_TICK, REACH,
                selected -> struck[1]++);

        assertEquals(257, still.visited(), "mobs one strike visits, the caster at the origin");
        assertEquals(6, struck[0], "mobs that strike crosses");
        assertEquals(426, away.visited(), "mobs one strike visits, the caster walked away");
        assertEquals(6, struck[1], "mobs that strike crosses");
    }

    @Test
    void frontCrossesWhatLiesPastLastTicksRadiusAndWithinThisOnes() {
        assertTrue(ScryStep.crossed(4.5, 4, 5));
        assertTrue(ScryStep.crossed(5, 4, 5));
        assertFalse(ScryStep.crossed(4, 4, 5));
        assertFalse(ScryStep.crossed(5.1, 4, 5));
    }
}

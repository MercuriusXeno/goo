package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ScryStep's front: the distances one tick's front crosses.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryStepTest {

    private static final double REACH = 96;
    private static final double ONE_TICK = 2.4;
    private static final double WALKED = 20;
    private static final int PAST_RANGE = 10;
    private static final double EXACT = 1e-9;

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

    /**
     * The shipped Scry reaches 32 blocks: one front, driven at its own pace
     * past its range, strikes a mob 32 blocks out and never one at 33.
     * decision scry-reach-drops-to-thirty-two
     */
    @Test
    void theShippedScryStrikesAMobAtThirtyTwoBlocksAndNeverOneAtThirtyThree() {
        ScryStep scry = AbilityJson.decode("astral_scry").behaviors().stream()
                .filter(ScryStep.class::isInstance).map(ScryStep.class::cast).findFirst().orElseThrow();
        Vec3 origin = HeldSweepWorld.CASTER;
        TargetHost atReach = hostAt(origin.add(32, 0, 0));
        TargetHost pastReach = hostAt(origin.add(33, 0, 0));
        EntityScanHost scan = scanOver(origin, Map.of(atReach, origin.add(32, 0, 0), pastReach, origin.add(33, 0, 0)));
        HeldSweepPace pace = scry.pace();
        Map<TargetHost, Integer> struck = new HashMap<>();

        for (int age = 1; age <= pace.ticksToRange() + PAST_RANGE; age++) {
            ScryStep.strikeCrossed(new ScryStep.Strike(scan, Set.of(), origin, origin), pace.radiusAt(age - 1),
                    pace.radiusAt(age), selected -> struck.merge(selected, 1, Integer::sum));
        }

        assertEquals(32, scry.reach(), EXACT, "the shipped reach");
        assertEquals(1, struck.getOrDefault(atReach, 0), "the mob 32 blocks out, struck once");
        assertEquals(0, struck.getOrDefault(pastReach, 0), "the mob 33 blocks out, never struck");
    }

    private static TargetHost hostAt(Vec3 at) {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.position()).thenReturn(at);
        TargetHost host = mock(TargetHost.class);
        when(host.target()).thenReturn(mob);
        return host;
    }

    /** A scan handing its body each mob within the scan's radius of where the caster stands. */
    private static EntityScanHost scanOver(Vec3 casterAt, Map<TargetHost, Vec3> mobs) {
        EntityScanHost scan = mock(EntityScanHost.class);
        doAnswer(call -> {
            double radius = call.getArgument(1);
            Consumer<TargetHost> body = call.getArgument(3);
            mobs.forEach((host, at) -> {
                if (at.distanceTo(casterAt) <= radius) {
                    body.accept(host);
                }
            });
            return null;
        }).when(scan).forEachEntityWithin(any(), anyDouble(), any(), any());
        return scan;
    }

    @Test
    void frontCrossesWhatLiesPastLastTicksRadiusAndWithinThisOnes() {
        assertTrue(ScryStep.crossed(4.5, 4, 5));
        assertTrue(ScryStep.crossed(5, 4, 5));
        assertFalse(ScryStep.crossed(4, 4, 5));
        assertFalse(ScryStep.crossed(5.1, 4, 5));
    }
}

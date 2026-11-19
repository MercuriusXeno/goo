package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Scry held a minute, its caster walking a circle through the fixed
 * synthetic world, does no more work in its last second than in its tenth:
 * once the first pulse's linger has run out, a hold has reached its steady
 * state, so nothing a long hold could pile up reads more at tick 1200 than
 * at tick 200.
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
class HeldSweepLongHoldTest {

    private static final int SECOND = 20;
    /** The walk reads a walking hold made in its last second at commit a14a3b3, before a moving caster's fields shared their walls. */
    private static final long WALKING_READS_AT_A14A3B3 = 1_828_857;
    private static final int TENTH_SECOND = 200;
    private static final int LAST_SECOND = 1_200;
    /**
     * A walking player's pace, about four blocks a second, around a circle
     * walked once each 250 ticks, so the tenth second and the last stand at
     * one spot of it and one moment between pulses.
     */
    private static final double WALK_A_TICK = 0.2;
    private static final int TICKS_A_LAP = 250;
    private static final double WALK_RADIUS = TICKS_A_LAP * WALK_A_TICK / (2 * Math.PI);
    private static final int MOBS = 24;
    private static final double MOB_RING = 30;
    private static final double MOB_HALF_WIDTH = 0.3;
    private static final double MOB_HEIGHT = 1.8;

    @Test
    void aMinuteLongScryHoldDoesNoMoreWorkInItsLastSecondThanItsTenth() {
        HeldSweepHold hold = HeldSweepHold.scry(new HeldSweepWorld()).walking(HeldSweepLongHoldTest::onTheCircle)
                .painting(mobsAround());

        for (int second = 1; second <= TENTH_SECOND / 20; second++) {
            hold.settle(20);
            System.out.println("LONG HOLD second " + second + " " + hold.quantities());
        }
        HeldSweepHold.Quantities tenth = hold.quantities();
        hold.settle(LAST_SECOND - TENTH_SECOND);
        HeldSweepHold.Quantities last = hold.quantities();

        String both = "tick " + TENTH_SECOND + " " + tenth + ", tick " + LAST_SECOND + " " + last;
        assertTrue(last.serverPulses() <= tenth.serverPulses(), "server pulses, " + both);
        assertTrue(last.clientPulses() <= tenth.clientPulses(), "client pulses, " + both);
        assertTrue(last.litWalls() <= tenth.litWalls(), "lit walls, " + both);
        assertTrue(last.fields() <= tenth.fields(), "fields, " + both);
        assertTrue(last.paintedMobs() <= tenth.paintedMobs(), "painted mobs, " + both);
        assertTrue(last.scannedThisTick() <= tenth.scannedThisTick(), "entities scanned, " + both);
        assertTrue(last.payloadsThisTick() <= tenth.payloadsThisTick(), "payloads sent, " + both);
        System.out.println("LONG HOLD " + both);
    }

    /**
     * A caster who walks while holding Scry sends each pulse from a new
     * spot, and each new spot's field of walls walked the cave from nothing
     * on the render thread (the operator found Scry lag hardest while
     * moving); a walking hold's walk reads in a second, a minute in, against
     * a still hold's and against commit a14a3b3's.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     */
    @Test
    void aWalkingScryHoldReadsFewerBlocksEachSecondThanItDidAtA14a3b3() {
        HeldSweepWorld world = new HeldSweepWorld();
        HeldSweepHold still = HeldSweepHold.scry(world);
        still.settle(LAST_SECOND - SECOND);
        long stillBefore = still.blockReads();
        still.settle(SECOND);
        long stillReads = still.blockReads() - stillBefore;
        HeldSweepHold walking = HeldSweepHold.scry(world).walking(HeldSweepLongHoldTest::onTheCircle);
        walking.settle(LAST_SECOND - SECOND);
        long walkingBefore = walking.blockReads();
        walking.settle(SECOND);
        long walkingReads = walking.blockReads() - walkingBefore;

        System.out.println("WALK READS still " + stillReads + ", walking " + walkingReads);
        assertTrue(walkingReads < WALKING_READS_AT_A14A3B3, "fewer walk reads than at a14a3b3, walking " + walkingReads);
        assertEquals(523_282, walkingReads, "a walking hold's walk reads in its last second; a still one's " + stillReads);
    }

    /** Where the caster stands on a held tick: walking a circle at a player's pace. */
    private static Vec3 onTheCircle(int held) {
        double angle = held * WALK_A_TICK / WALK_RADIUS;
        return HeldSweepWorld.CASTER.add(WALK_RADIUS * Math.cos(angle) - WALK_RADIUS, 0, WALK_RADIUS * Math.sin(angle));
    }

    private static List<Entity> mobsAround() {
        List<Entity> mobs = new ArrayList<>();
        for (int mob = 0; mob < MOBS; mob++) {
            double angle = 2 * Math.PI * mob / MOBS;
            Vec3 at = HeldSweepWorld.CASTER.add(MOB_RING * Math.cos(angle), 0, MOB_RING * Math.sin(angle));
            LivingEntity living = mock(LivingEntity.class);
            when(living.getBoundingBox()).thenReturn(new AABB(at.add(-MOB_HALF_WIDTH, 0, -MOB_HALF_WIDTH),
                    at.add(MOB_HALF_WIDTH, MOB_HEIGHT, MOB_HALF_WIDTH)));
            when(living.getId()).thenReturn(mob);
            mobs.add(living);
        }
        return mobs;
    }
}

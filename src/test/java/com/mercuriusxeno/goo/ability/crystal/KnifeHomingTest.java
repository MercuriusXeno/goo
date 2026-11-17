package com.mercuriusxeno.goo.ability.crystal;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A thrown knife leaves at 1.5 blocks a tick, a snowball's speed, with no
 * gravity, and each tick turns toward its mob where the mob stands now, so
 * its flight ends on the mob though the mob moves (decisions
 * knives-home-on-their-painted-mob, knives-leave-the-blob-at-a-snowballs-speed).
 */
class KnifeHomingTest {

    private static final double WITHIN = 1e-9;
    /** Blocks the mob steps sideways each tick, slower than the knife. */
    private static final double SIDESTEP = 0.3;

    @Test
    void theFirstStepLeavesAtASnowballsSpeed() {
        Vec3 velocity = KnifeHoming.velocityToward(Vec3.ZERO, new Vec3(3, 2, 10));

        assertEquals(1.5, velocity.length(), WITHIN);
    }

    @Test
    void aKnifeEndsInsideTheBoxOfAMobSteppingSidewaysEachTick() {
        Vec3 position = Vec3.ZERO;
        AABB mob = new AABB(-0.3, 0, 9.7, 0.3, 1.8, 10.3);
        for (int tick = 0; tick < KnifeHoming.MOST_FLIGHT_TICKS; tick++) {
            Vec3 next = position.add(KnifeHoming.velocityToward(position, mob.getCenter()));
            if (KnifeHoming.arrives(position, next, mob)) {
                return;
            }
            position = next;
            mob = mob.move(SIDESTEP, 0, 0);
        }
        assertTrue(false, "the knife at " + position + " never reached the mob at " + mob);
    }
}

package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.GooStreamHandler;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Mycosis spore leaves the glove fast enough to outrun a sprint-jumping
 * player and still comes to rest where the server sprayed it to rest, as far
 * off as the crosshair lands (decisions glove-stream-motes-carry-the-players-travel-velocity,
 * mycosis-spore-stream-buds-and-poisons).
 */
class SporeParticleTest {

    /** Three blocks off, a crosshair on the ground a few steps ahead. */
    private static final double REST = 3;
    private static final Vec3 AHEAD = new Vec3(0, 0, 1);
    private static final double DELTA = 1e-6;
    private static final int COAST_TICKS = 400;

    /** The spore the server sprays to rest three blocks off, as the client launches it. */
    private static Vec3 launchedToRestThreeBlocksOff() {
        return SporeParticle.driftLaunch(AHEAD.scale(GooStreamHandler.restingSpeed(REST)));
    }

    @Test
    void aSporeLeavesTheGloveTwiceAsFastAsASprintJumpingPlayerRuns() {
        double speed = launchedToRestThreeBlocksOff().length();

        assertTrue(speed >= 2 * GnatSteeringTest.SPRINT_JUMPING, "a spore sprayed to rest three blocks off leaves at "
                + speed + " blocks a tick, a sprint-jumping player runs " + GnatSteeringTest.SPRINT_JUMPING);
    }

    @Test
    void aSporeStillComesToRestWhereTheServerSprayedItToRest() {
        // Particle.tick moves a mote by its velocity, then keeps the friction's share of it
        Vec3 velocity = launchedToRestThreeBlocksOff();
        double coasted = 0;
        for (int tick = 0; tick < COAST_TICKS; tick++) {
            coasted += velocity.length();
            velocity = velocity.scale(SporeParticle.DRIFT_FRICTION);
        }

        assertEquals(REST, coasted, DELTA, "the spore should coast to its rest");
    }
}

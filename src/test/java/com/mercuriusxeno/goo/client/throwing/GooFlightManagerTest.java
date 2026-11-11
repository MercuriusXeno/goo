package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A goo flight hands its trail the blob's real velocity, the distance its
 * position covers in one tick, drift included, rather than its unit heading
 * (decision thrown-blob-trail-takes-the-blobs-velocity).
 */
class GooFlightManagerTest {

    private static final Vec3 HAND = new Vec3(0.5, 65.5, 0.5);
    /** Twenty blocks out over ten ticks, so the blob covers about two blocks a tick, far from a unit heading. */
    private static final Vec3 TARGET = new Vec3(20.5, 63.5, 0.5);
    private static final int TRAVEL_TICKS = 10;
    private static final Vec3 SPRINTING = new Vec3(0.28, 0.0, 0.1);
    /** getPosition reads its progress as a float, so the two reads agree to a float's precision. */
    private static final double CLOSE = 1e-5;

    private static GooFlightManager.GooFlight flight(Vec3 travel) {
        return new GooFlightManager.GooFlight(HAND, TARGET, -1, BlockPos.containing(TARGET), GooTypes.CRYSTAL,
                Delivery.ARC, TRAVEL_TICKS, false, travel);
    }

    private static Vec3 at(GooFlightManager.GooFlight flight, int tick) {
        flight.ticksElapsed = tick;
        return flight.getPosition(0F);
    }

    @Test
    void theTrailIsGivenTheDistanceTheBlobCoversInOneTickAtMidFlight() {
        GooFlightManager.GooFlight flight = flight(SPRINTING);
        int middle = TRAVEL_TICKS / 2;
        double covered = at(flight, middle + 1).distanceTo(at(flight, middle));

        flight.ticksElapsed = middle;
        assertEquals(covered, flight.getTickVelocity(0F).length(), CLOSE);
    }

    @Test
    void theTrailIsGivenTheDriftedStepOffTheHand() {
        GooFlightManager.GooFlight flight = flight(SPRINTING);
        Vec3 step = at(flight, 1).subtract(at(flight, 0));

        flight.ticksElapsed = 0;
        Vec3 given = flight.getTickVelocity(0F);
        assertEquals(step.x, given.x, CLOSE);
        assertEquals(step.y, given.y, CLOSE);
        assertEquals(step.z, given.z, CLOSE);
    }
}

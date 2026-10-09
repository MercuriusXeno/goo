package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.TravelingStep;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a traveling blob is each tick of its flight: on the clients' arc,
 * from the hand at the throw to the target at the landing, and done once it lands.
 */
class TravelingFlightsTest {

    private static final Vec3 HAND = new Vec3(0, 2, 0);
    private static final Vec3 TARGET = new Vec3(10, 1, 0);
    private static final double PEAK = 1.5;
    private static final int THROWN_AT = 100;
    private static final int TRAVEL = 20;

    private final TravelingFlights.Flight flight = new TravelingFlights.Flight(null, "goo:frost_orb", HAND, TARGET,
            PEAK, THROWN_AT, TRAVEL, new TravelingStep(3f, List.of()));

    @Test
    void theBlobStartsAtTheHandAndEndsAtTheTarget() {
        assertEquals(HAND, flight.at(THROWN_AT));
        assertEquals(TARGET, flight.at(THROWN_AT + TRAVEL));
    }

    @Test
    void midFlightTheBlobRidesTheClientsArc() {
        assertEquals(ThrowArc.arcPoint(HAND, TARGET, 0.5, PEAK), flight.at(THROWN_AT + TRAVEL / 2));
    }

    @Test
    void theFlightIsDoneFromItsLandingTick() {
        assertFalse(flight.landedBy(THROWN_AT + TRAVEL - 1));
        assertTrue(flight.landedBy(THROWN_AT + TRAVEL));
    }
}

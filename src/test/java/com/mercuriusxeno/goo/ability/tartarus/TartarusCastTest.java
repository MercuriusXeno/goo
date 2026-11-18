package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers whether a Tartarus hold is refused and what it charges, read from the spot its placement answered.
 */
class TartarusCastTest {

    private static final TartarusDestination NETHER_AT = new TartarusDestination(Level.NETHER,
            new BlockPos(100, 60, -200));
    private static final int SHARE = 100;
    private static final float YAW = 45f;

    /** decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses */
    @Test
    void aPlacementAnsweringNoneRefusesTheCastAndChargesNothing() {
        TartarusCast cast = TartarusCast.resolve(Optional.of(NETHER_AT), destination -> Optional.empty(), YAW);

        assertTrue(cast.refused(), "refused");
        assertEquals(0, cast.charged(SHARE));
    }

    @Test
    void noDestinationRefusesTheCastAndChargesNothing() {
        TartarusCast cast = TartarusCast.resolve(Optional.empty(), destination -> Optional.of(BlockPos.ZERO), YAW);

        assertTrue(cast.refused(), "refused");
        assertEquals(0, cast.charged(SHARE));
    }

    @Test
    void aPlacementAnsweringASpotChargesTheShareAndArrivesThere() {
        BlockPos spot = new BlockPos(101, 64, -199);
        TartarusCast cast = TartarusCast.resolve(Optional.of(NETHER_AT), destination -> Optional.of(spot), YAW);

        assertFalse(cast.refused(), "carried");
        assertEquals(SHARE, cast.charged(SHARE));
        assertEquals(Optional.of(new TartarusArrival(NETHER_AT, spot, YAW)), cast.arrival());
    }
}

package com.mercuriusxeno.goo.ability.end;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers whether End stands available or grays out, read from the player's level and the way back they keep
 * (decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability).
 */
class EndAvailabilityTest {

    @Test
    void endGraysOutInTheEndForAPlayerHoldingNoWayBack() {
        assertEquals(EndAvailability.GRAYED_OUT, EndAvailability.of(Level.END, false));
    }

    @Test
    void endStandsAvailableInTheEndForAPlayerTheGateCarriedThere() {
        assertEquals(EndAvailability.AVAILABLE, EndAvailability.of(Level.END, true));
    }

    @Test
    void endStandsAvailableInTheOverworld() {
        assertEquals(EndAvailability.AVAILABLE, EndAvailability.of(Level.OVERWORLD, false));
    }
}

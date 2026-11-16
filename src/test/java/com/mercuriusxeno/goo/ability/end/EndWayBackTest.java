package com.mercuriusxeno.goo.ability.end;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the way back a player keeps as they change level: kept while they stay in the End, gone once they leave
 * it by any way (decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability).
 */
class EndWayBackTest {

    private static final EndWayBack HELD = new EndWayBack(Optional.of(
            EndDestination.wayBackFrom(Level.OVERWORLD, new Vec3(12.5, 64, -40.5), 15f)));

    @Test
    void aWayBackReadsEmptyOnceThePlayerLeavesTheEnd() {
        assertEquals(EndWayBack.NONE, HELD.afterChangingDimension(Level.END, Level.OVERWORLD));
    }

    @Test
    void aWayBackStandsAsThePlayerArrivesInTheEnd() {
        assertEquals(HELD, HELD.afterChangingDimension(Level.OVERWORLD, Level.END));
    }
}

package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers a blink's cost sum: the flat cost, plus the per-block amount for
 * the blocks travelled rounded up, plus the wall surcharge when the trip
 * passed through a solid block (decision blink-lands-safely-costed-by-distance).
 */
class DistancePriceTest {

    private static final int BASE = 200;
    private static final DistancePrice BLINK = new DistancePrice(100, 400);

    private static Optional<BlinkLanding> trip(double distance, boolean throughWall) {
        return Optional.of(new BlinkLanding(Vec3.ZERO, distance, throughWall));
    }

    @Test
    void aClearTripAddsItsBlocks() {
        assertEquals(BASE + 800, BLINK.priceOf(BASE, trip(8, false)));
    }

    @Test
    void aTripThroughAWallAddsTheSurcharge() {
        assertEquals(BASE + 800 + 400, BLINK.priceOf(BASE, trip(8, true)));
    }

    @Test
    void aPartBlockRoundsUp() {
        assertEquals(BASE + 211, BLINK.priceOf(BASE, trip(2.101, false)));
    }

    @Test
    void noTripPaysTheFlatCost() {
        assertEquals(BASE, BLINK.priceOf(BASE, Optional.empty()));
        assertEquals(BASE, DistancePrice.NONE.priceOf(BASE, trip(8, true)));
    }
}

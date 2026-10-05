package com.mercuriusxeno.goo.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bark lost while burning burns away right to left, the rightmost half first, and bark lost out of fire burns nothing (decision bark-hearts-burn-away-right-to-left). */
class BarkBurnsTest {

    private static final float NOW = 100f;
    private static final float DELTA = 1e-5f;

    @Test
    void aDropWhileBurningBurnsExactlyTheLostHalvesRightmostFirst() {
        List<BarkBurns.Burn> burns = BarkBurns.burnsFor(List.of(2, 2, 2), List.of(2, 1, 0), true, NOW);
        assertEquals(List.of(new BarkBurns.Burn(2, 1, NOW), new BarkBurns.Burn(2, 0, NOW + BarkBurns.STAGGER_TICKS),
                new BarkBurns.Burn(1, 1, NOW + 2 * BarkBurns.STAGGER_TICKS)), burns);
    }

    @Test
    void aDropOutOfFireBurnsNothing() {
        assertTrue(BarkBurns.burnsFor(List.of(2, 2, 2), List.of(2, 1, 0), false, NOW).isEmpty());
    }

    @Test
    void regrowthBurnsNothing() {
        assertTrue(BarkBurns.burnsFor(List.of(2, 1), List.of(2, 2), true, NOW).isEmpty());
    }

    @Test
    void theFrontCrossesTheHalfFromItsRightEdgeToItsLeft() {
        BarkBurns.Burn burn = new BarkBurns.Burn(0, 1, NOW);
        assertEquals(0, burn.burnedFromRight(NOW));
        assertEquals(0.5f, burn.progress(NOW + BarkBurns.BURN_TICKS / 2), DELTA);
        assertTrue(burn.burnedFromRight(NOW + BarkBurns.BURN_TICKS / 2) > 0);
        assertEquals(RegrowCrawl.HALF_WIDTH, burn.burnedFromRight(NOW + BarkBurns.BURN_TICKS));
    }

    @Test
    void barkskinBurningOutPlaysItsBurnsThenClears() {
        BarkBurns tracker = new BarkBurns();
        HeartOverlay barked = HeartOverlay.NONE.apply(HeartKind.BARKSKIN, 1_200, 4f, 0L);
        tracker.update(barked, true, NOW);
        List<BarkBurns.Burn> burning = tracker.update(HeartOverlay.NONE, true, NOW + 1);
        assertEquals(4, burning.size());
        assertEquals(1, burning.getFirst().slot());
        assertTrue(tracker.update(HeartOverlay.NONE, true, NOW + 1 + 3 * BarkBurns.STAGGER_TICKS
                + BarkBurns.BURN_TICKS).isEmpty());
    }
}

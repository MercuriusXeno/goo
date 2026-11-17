package com.mercuriusxeno.goo.ability.crystal;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A throw spreads its knives evenly across the painted mobs in painted order,
 * an uneven remainder going to the mobs painted first; a mob takes no more
 * than its knives-to-kill, its spare knives passing to the next mob and past
 * the last wrapping round to the earliest still short, and knives no mob
 * takes stay in the fan (decision knives-split-evenly-capped-at-the-kill; the
 * operator ruled the wrap).
 */
class KnifeSplitTest {

    @Test
    void twelveKnivesOverThreeToughMobsSplitFourFourFour() {
        assertEquals(new KnifeSplit.Split(List.of(4, 4, 4), 0), KnifeSplit.split(12, List.of(10, 10, 10)));
    }

    @Test
    void sevenKnivesGiveTheRemainderToTheMobPaintedFirst() {
        assertEquals(new KnifeSplit.Split(List.of(3, 2, 2), 0), KnifeSplit.split(7, List.of(10, 10, 10)));
    }

    @Test
    void aMobsSpareKnivesPassToTheNextMob() {
        assertEquals(new KnifeSplit.Split(List.of(1, 7, 4), 0), KnifeSplit.split(12, List.of(1, 10, 10)));
    }

    @Test
    void knivesNoMobTakesStayInTheFan() {
        assertEquals(new KnifeSplit.Split(List.of(2, 3), 7), KnifeSplit.split(12, List.of(2, 3)));
    }

    @Test
    void theLastMobsSparesWrapToTheEarliestMobStillShort() {
        assertEquals(new KnifeSplit.Split(List.of(10, 1), 1), KnifeSplit.split(12, List.of(10, 1)));
    }

    @Test
    void nothingPaintedThrowsNothing() {
        assertEquals(new KnifeSplit.Split(List.of(), 5), KnifeSplit.split(5, List.of()));
    }
}

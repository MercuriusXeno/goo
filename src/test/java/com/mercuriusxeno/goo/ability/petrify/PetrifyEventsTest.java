package com.mercuriusxeno.goo.ability.petrify;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The stone crackles once every crackle interval while the fog fills a mob,
 * not every tick (decision petrify-stone-encasement-and-calcify-map).
 */
class PetrifyEventsTest {

    @Test
    void oneTickInEachIntervalCrackles() {
        long crackles = 0;
        long ticks = PetrifyEvents.CRACKLE_INTERVAL_TICKS * 5;
        for (long tick = 0; tick < ticks; tick++) {
            crackles += PetrifyEvents.cracklesOn(tick) ? 1 : 0;
        }
        assertEquals(5, crackles);
    }
}

package com.mercuriusxeno.goo.ability.reap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reaped crop's seeds settle against the seed it was replanted from: the
 * replanted seeds are paid out of those dropped, and what is left never
 * falls below none. Gametest reap_settles_seeds_against_the_replant settles
 * real drops.
 * reap-breeze-harvests-and-replants
 */
class ReapingSeedsTest {

    @ParameterizedTest
    @CsvSource({"0, 1, 0", "1, 1, 0", "2, 1, 1", "4, 1, 3"})
    void seedsLeftPaysTheReplantedOutOfTheDroppedAndClampsAtNone(int dropped, int replanted, int left) {
        assertEquals(left, Reaping.seedsLeft(dropped, replanted));
    }
}

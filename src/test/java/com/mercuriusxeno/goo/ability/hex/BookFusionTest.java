package com.mercuriusxeno.goo.ability.hex;

import com.mercuriusxeno.goo.ability.hex.BookFusion.SlotPair;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Fuse's choice of pair, the first two identical books in slot order
 * that can still rise, and the level a fused enchantment reaches.
 */
class BookFusionTest {

    private static final Predicate<String> ANY_RISES = held -> true;
    private static final String SHARPNESS_ONE = "sharpness 1";
    private static final String UNBREAKING_ONE = "unbreaking 1";
    private static final String MENDING_ONE = "mending 1";

    @Test
    void pairsTheFirstIdenticalBooksInSlotOrder() {
        List<Optional<String>> slots = List.of(Optional.of(UNBREAKING_ONE), Optional.empty(),
                Optional.of(SHARPNESS_ONE), Optional.of(UNBREAKING_ONE), Optional.of(SHARPNESS_ONE));

        assertEquals(Optional.of(new SlotPair(0, 3)), BookFusion.firstIdenticalPair(slots, ANY_RISES));
    }

    @Test
    void findsNoPairAmongDifferentBooks() {
        List<Optional<String>> slots = List.of(Optional.of(SHARPNESS_ONE), Optional.of(UNBREAKING_ONE),
                Optional.empty());

        assertEquals(Optional.empty(), BookFusion.firstIdenticalPair(slots, ANY_RISES));
    }

    @Test
    void passesOverAPairWithNothingLeftToRaise() {
        List<Optional<String>> slots = List.of(Optional.of(MENDING_ONE), Optional.of(MENDING_ONE),
                Optional.of(SHARPNESS_ONE), Optional.of(SHARPNESS_ONE));

        assertEquals(Optional.of(new SlotPair(2, 3)),
                BookFusion.firstIdenticalPair(slots, held -> !MENDING_ONE.equals(held)));
    }

    @Test
    void aFusedLevelRisesByOne() {
        assertEquals(2, BookFusion.fusedLevel(1, 5));
    }

    @Test
    void aFusedLevelHoldsAtItsMaximum() {
        assertEquals(5, BookFusion.fusedLevel(5, 5));
    }
}

package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A value once seen is answered after it is gone, the latest seen winning,
 * until its key is no longer kept (decision unmake-waves-dissolve-by-crucible-cost).
 */
class RememberedTest {

    private static final String BLOCK = "block";
    private static final String COBBLESTONE = "cobblestone";
    private static final String STONE = "stone";

    @Test
    void aValueOnceSeenIsAnsweredAfterItIsGone() {
        Remembered<String, String> remembered = new Remembered<>();

        assertNull(remembered.of(BLOCK, null));
        assertEquals(COBBLESTONE, remembered.of(BLOCK, COBBLESTONE));
        assertEquals(COBBLESTONE, remembered.of(BLOCK, null));
    }

    @Test
    void theLatestValueSeenWins() {
        Remembered<String, String> remembered = new Remembered<>();
        remembered.of(BLOCK, COBBLESTONE);
        remembered.of(BLOCK, STONE);

        assertEquals(STONE, remembered.of(BLOCK, null));
    }

    @Test
    void aKeyNotKeptIsForgotten() {
        Remembered<String, String> remembered = new Remembered<>();
        remembered.of(BLOCK, COBBLESTONE);

        remembered.keepOnly(Set.of(STONE));

        assertNull(remembered.of(BLOCK, null));
    }
}

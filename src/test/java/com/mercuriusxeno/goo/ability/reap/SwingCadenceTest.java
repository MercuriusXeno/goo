package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cadence Reap's held channel slashes at: a slash on the hold's
 * first tick, then each next a random two or three ticks after the last, both
 * gaps turning up.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
class SwingCadenceTest {

    private static final int FEWEST = 2;
    private static final int MOST = 3;
    private static final long NOW = 100;

    @Test
    void everyGapIsTwoOrThreeTicksAndBothTurnUp() {
        RandomSource random = RandomSource.create(3L);
        Set<Long> gaps = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            gaps.add(SwingCadence.nextSwingAt(NOW, FEWEST, MOST, random) - NOW);
        }
        assertEquals(Set.of(2L, 3L), gaps);
    }

    @Test
    void theHoldsFirstTickSwings() {
        assertTrue(SwingCadence.swingsNow(1, NOW, NOW + 50));
    }

    @Test
    void aTickBeforeTheNextSwingSwingsNothing() {
        assertFalse(SwingCadence.swingsNow(4, NOW, NOW + 1));
    }

    @Test
    void theNextSwingsTickSwings() {
        assertTrue(SwingCadence.swingsNow(4, NOW, NOW));
    }

}

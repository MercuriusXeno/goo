package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the cadence Reap's held channel slashes at: a slash on the hold's
 * first tick, then each next a random two or three ticks after the last, both
 * gaps turning up; and the forms the slashes take in turn, a downward
 * diagonal, an upward diagonal and a flat one, as the Crissaegrim's do.
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

    @Test
    void theSlashesCycleDownUpAndFlat() {
        SlashForm form = SlashForm.DOWN_DIAGONAL;
        List<SlashForm> seen = new java.util.ArrayList<>();
        for (int slash = 0; slash < 6; slash++) {
            seen.add(form);
            form = form.next();
        }
        assertEquals(List.of(SlashForm.DOWN_DIAGONAL, SlashForm.UP_DIAGONAL, SlashForm.FLAT,
                SlashForm.DOWN_DIAGONAL, SlashForm.UP_DIAGONAL, SlashForm.FLAT), seen);
    }
}

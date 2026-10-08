package com.mercuriusxeno.goo.client.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sight's fungus scan runs at once when none has run, then once a second,
 * and again when the clock runs back; the first scan was never due while the
 * unscanned mark overflowed the elapsed time
 * (decision sight-lengthens-shift-and-outlines-fungus).
 */
class FungusXrayTest {

    private static final long NOW = 120_000L;

    @Test
    void theFirstScanIsDueAtOnce() {
        assertTrue(FungusXray.isDue(FungusXray.UNSCANNED, NOW));
    }

    @Test
    void aScanIsDueOnceASecondHasPassed() {
        assertFalse(FungusXray.isDue(NOW, NOW + FungusXray.RESCAN_TICKS - 1));
        assertTrue(FungusXray.isDue(NOW, NOW + FungusXray.RESCAN_TICKS));
    }

    @Test
    void aScanIsDueWhenTheClockRunsBack() {
        assertTrue(FungusXray.isDue(NOW, NOW - 1));
    }
}

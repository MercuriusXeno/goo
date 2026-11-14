package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.Sight;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** The outlines fade over the second after the sight ends (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
    @Nested
    class AfterTheSightEnds {

        private static final float EPSILON = 1e-6f;
        private static final int ALPHA_TRANSMIT = 24;
        private final Afterglow<Sight> glow = new Afterglow<>();

        @BeforeEach
        void endTheSightNow() {
            FungusXray.outlineStrength(glow, new Sight(0.5f, NOW), NOW - 1, NOW - 1);
            FungusXray.outlineStrength(glow, Sight.NONE, NOW, NOW);
        }

        @Test
        void halfASecondAfterTheOutlinesDrawAtHalfAlpha() {
            float strength = FungusXray.outlineStrength(glow, Sight.NONE, NOW + 10, NOW + Afterglow.FADE_TICKS / 2f);
            assertEquals(0.5f, strength, EPSILON);
            assertEquals(Math.round((FungusXray.GHOST_TINT >>> ALPHA_TRANSMIT) * 0.5f),
                    FungusXray.ghostTint(strength) >>> ALPHA_TRANSMIT);
        }

        @Test
        void aSecondAfterTheOutlinesAreGone() {
            assertEquals(0f, FungusXray.outlineStrength(glow, Sight.NONE, NOW + Afterglow.FADE_TICKS,
                    NOW + Afterglow.FADE_TICKS), EPSILON);
        }
    }
}

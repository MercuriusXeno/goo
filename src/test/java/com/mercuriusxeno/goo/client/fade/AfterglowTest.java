package com.mercuriusxeno.goo.client.fade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A trace reads full while its state stands and falls to nothing over the second after it is first seen gone (decision brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends). */
class AfterglowTest {

    private static final float DELTA = 1e-6f;
    private static final float GONE_AT = 100f;
    private static final String STATE = "kindle";

    @Test
    void theFadeSpansASecond() {
        assertEquals(20, Afterglow.FADE_TICKS);
    }

    @Nested
    class OneTrace {

        @Test
        void aStandingStateReadsFull() {
            Afterglow<String> glow = new Afterglow<>();
            Afterglow.Glow<String> read = glow.read(STATE, GONE_AT).orElseThrow();
            assertEquals(STATE, read.state());
            assertEquals(1f, read.strength(), DELTA);
        }

        @Test
        void aGoneStateFallsToHalfAtHalfTheFade() {
            Afterglow<String> glow = goneAt(GONE_AT);
            Afterglow.Glow<String> read = glow.read(null, GONE_AT + Afterglow.FADE_TICKS / 2f).orElseThrow();
            assertEquals(STATE, read.state());
            assertEquals(0.5f, read.strength(), DELTA);
        }

        @Test
        void theFadeEasesThroughThePartialTick() {
            Afterglow<String> glow = goneAt(GONE_AT);
            assertEquals(0.975f, glow.read(null, GONE_AT + 0.5f).orElseThrow().strength(), DELTA);
        }

        @Test
        void aGoneStateReadsNothingOnceTheFadeIsSpent() {
            Afterglow<String> glow = goneAt(GONE_AT);
            assertTrue(glow.read(null, GONE_AT + Afterglow.FADE_TICKS).isEmpty());
            assertEquals(0f, Afterglow.strength(GONE_AT, GONE_AT + Afterglow.FADE_TICKS), DELTA);
        }

        @Test
        void aSpentTraceStaysForgotten() {
            Afterglow<String> glow = goneAt(GONE_AT);
            glow.read(null, GONE_AT + Afterglow.FADE_TICKS);
            assertTrue(glow.read(null, GONE_AT + 1f).isEmpty());
        }

        @Test
        void aStateStandingAgainBeforeTheFadeIsSpentReadsFullAgain() {
            Afterglow<String> glow = goneAt(GONE_AT);
            glow.read(null, GONE_AT + 5f);
            assertEquals(1f, glow.read(STATE, GONE_AT + 6f).orElseThrow().strength(), DELTA);
        }

        @Test
        void aStateGoneAgainFadesFromItsNewEnd() {
            Afterglow<String> glow = goneAt(GONE_AT);
            glow.read(STATE, GONE_AT + 5f);
            glow.read(null, GONE_AT + 6f);
            float halfway = GONE_AT + 6f + Afterglow.FADE_TICKS / 2f;
            assertEquals(0.5f, glow.read(null, halfway).orElseThrow().strength(), DELTA);
        }

        @Test
        void aClockRunningBackwardsSpendsTheTrace() {
            Afterglow<String> glow = goneAt(GONE_AT);
            assertTrue(glow.read(null, GONE_AT - 1f).isEmpty());
        }

        @Test
        void nothingEverStandingReadsNothing() {
            assertTrue(new Afterglow<String>().read(null, GONE_AT).isEmpty());
        }

        private static Afterglow<String> goneAt(float tick) {
            Afterglow<String> glow = new Afterglow<>();
            glow.read(STATE, tick - 1f);
            glow.read(null, tick);
            return glow;
        }
    }

    @Nested
    class KeyedTraces {

        @Test
        void aGoneTraceFadesInItsPlaceBesideTheStandingOnes() {
            Afterglows<String, String> glows = new Afterglows<>();
            glows.read(standing("sight", "lux"), GONE_AT - 1f);
            glows.read(standing("lux"), GONE_AT);
            List<Afterglow.Glow<String>> read = glows.read(standing("lux"), GONE_AT + Afterglow.FADE_TICKS / 2f);
            assertEquals(List.of("sight", "lux"), read.stream().map(Afterglow.Glow::state).toList());
            assertEquals(0.5f, read.get(0).strength(), DELTA);
            assertEquals(1f, read.get(1).strength(), DELTA);
        }

        @Test
        void aSpentTraceLeavesTheSet() {
            Afterglows<String, String> glows = new Afterglows<>();
            glows.read(standing("sight"), GONE_AT - 1f);
            glows.read(standing(), GONE_AT);
            assertTrue(glows.read(standing(), GONE_AT + Afterglow.FADE_TICKS).isEmpty());
        }

        private static Map<String, String> standing(String... keys) {
            Map<String, String> states = new LinkedHashMap<>();
            for (String key : keys) {
                states.put(key, key);
            }
            return states;
        }
    }

    @Test
    void alphaScalesByTheStrength() {
        assertEquals(0x80FF5555, Afterglow.fadeArgb(0xFFFF5555, 0.5f));
        assertEquals(0x00FFFFFF, Afterglow.fadeArgb(0xFFFFFFFF, 0f));
    }
}

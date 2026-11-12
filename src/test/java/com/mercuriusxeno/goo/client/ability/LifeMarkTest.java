package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers what a mob's life draws over its head: one heart filled by its life
 * over its max, and its life counted in hearts as a mixed number.
 * health-bar-is-one-heart-filling-left-to-right
 */
class LifeMarkTest {

    private static final float EPSILON = 1e-6f;

    @Nested
    class FillShare {

        @Test
        void fullLifeFillsTheWholeHeart() {
            assertEquals(1f, LifeMark.of(20f, 20f).fill(), EPSILON);
        }

        @Test
        void halfLifeFillsHalfTheHeart() {
            assertEquals(0.5f, LifeMark.of(10f, 20f).fill(), EPSILON);
        }

        @Test
        void slimLifeFillsItsShare() {
            assertEquals(0.02f, LifeMark.of(0.4f, 20f).fill(), EPSILON);
        }

        @Test
        void smallMaxDrawsTheSameOneHeartMark() {
            LifeMark mark = LifeMark.of(7f, 10f);
            assertEquals(0.7f, mark.fill(), EPSILON);
            assertEquals("3¹⁄₂", mark.count());
        }
    }

    @Nested
    class CountInHearts {

        @Test
        void donkeyReadsTwelveAndAHalf() {
            assertEquals("12¹⁄₂", LifeMark.of(25f, 25f).count());
        }

        @Test
        void wholeHeartsReadWhole() {
            assertEquals("10", LifeMark.of(20f, 20f).count());
        }

        @Test
        void oddHealthReadsAHalf() {
            assertEquals("6¹⁄₂", LifeMark.of(13f, 20f).count());
        }

        @Test
        void slimLifeRoundsUpToHalfAHeart() {
            assertEquals("¹⁄₂", LifeMark.of(0.4f, 20f).count());
        }
    }
}

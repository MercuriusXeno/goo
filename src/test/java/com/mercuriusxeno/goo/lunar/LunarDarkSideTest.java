package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's dark side: every x past 3.75 million either way, where the sky reads as night at every hour while the
 * light side keeps its day and night on the overworld's clock.
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
class LunarDarkSideTest {

    /** The sky darkening lunar_day's timeline gives at noon: its sky light level of 15, darkened by nothing. */
    private static final int NOON_DARKEN = 0;
    /** The sky darkening lunar_day's timeline gives at midnight: 15 less its night sky light level of 4. */
    private static final int MIDNIGHT_DARKEN = 11;
    private static final double DARK_SIDE_X = 5_000_000;
    private static final double LIGHT_SIDE_X = 0;

    @Nested
    class TheLine {

        @ParameterizedTest
        @ValueSource(doubles = {0, 3_749_999, -3_749_999})
        void theBandAroundXZeroIsTheLightSide(double x) {
            assertFalse(Lunar.isDarkSide(x));
        }

        @ParameterizedTest
        @ValueSource(doubles = {3_750_001, -3_750_001, 7_500_000, -7_500_000})
        void everyXPastThreePointSevenFiveMillionIsTheDarkSide(double x) {
            assertTrue(Lunar.isDarkSide(x));
        }
    }

    @Nested
    class TheNight {

        @Test
        void theDarkSideReadsAsNightAtNoon() {
            assertTrue(LunarNight.isNightAt(DARK_SIDE_X, NOON_DARKEN));
        }

        @Test
        void theDarkSideReadsAsNightAtMidnight() {
            assertTrue(LunarNight.isNightAt(DARK_SIDE_X, MIDNIGHT_DARKEN));
        }

        @Test
        void theLightSideReadsAsDayAtNoon() {
            assertFalse(LunarNight.isNightAt(LIGHT_SIDE_X, NOON_DARKEN));
        }
    }
}

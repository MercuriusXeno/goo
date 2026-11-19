package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's dark side: every x past 3.75 million either way, the half of the moon that never sees the blue planet. Its
 * days and nights follow the sun as the light side's do, which LunarNightTest covers.
 * decision dark-side-is-the-half-of-the-moon-past-the-light-side
 */
class LunarDarkSideTest {

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
}

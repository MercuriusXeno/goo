package com.mercuriusxeno.goo.ability.root;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The vines a Vines cell gives by its layers, the slow and the time a count
 * of vines holds for, and what touching a cell does to the vines held.
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 */
class VineHoldTest {

    private static final int TICKS_A_SECOND = 20;
    private static final double EPSILON = 1e-9;

    @ParameterizedTest
    @CsvSource({"1, 1", "2, 1", "3, 2", "5, 2", "6, 3", "9, 3", "10, 4", "14, 4", "15, 5"})
    void aCellGivesVinesByItsLayers(int layers, int vines) {
        assertEquals(vines, VineHold.vinesFrom(layers));
    }

    @Nested
    class Hold {

        @ParameterizedTest
        @CsvSource({"1, 0.8, 4", "2, 0.6, 5", "3, 0.4, 6", "4, 0.2, 7", "5, 0, 8"})
        void eachVineSlowsByAFifthAndAddsASecondToThree(int vines, double speed, int seconds) {
            assertEquals(speed, VineHold.speedOf(vines), EPSILON);
            assertEquals(seconds * TICKS_A_SECOND, VineHold.ticksOf(vines));
        }

        @Test
        void vinesFromASecondCellAddUpToFive() {
            VineHold three = new VineHold(3, 2 * TICKS_A_SECOND);

            assertEquals(5, three.touched(6).vines());
        }
    }

    @Nested
    class Refresh {

        @Test
        void aThinCellRefreshesShortVinesUpToItsOwnThreshold() {
            VineHold five = new VineHold(5, 2 * TICKS_A_SECOND);

            assertEquals(new VineHold(5, 4 * TICKS_A_SECOND), five.touched(1));
        }

        @Test
        void aThinCellNeverShortensTheTimeLeft() {
            VineHold five = new VineHold(5, 7 * TICKS_A_SECOND);

            assertEquals(new VineHold(5, 7 * TICKS_A_SECOND), five.touched(1));
        }
    }
}

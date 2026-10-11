package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urchin's spear arms over three ticks, fires over three into the mob, and
 * retracts over six, its rods each keeping their length as they telescope
 * (decision urchin-rests-bristled-and-spears; operator ruling 2026-10-11:
 * arm 3, fire 3, retract 6, damage on contact).
 */
class UrchinSpearTest {

    private static final int STRIKE_TICK = 6;
    private static final int LENGTH = 12;
    private static final float REACH = 3.5f;
    private static final float EPSILON = 1e-5f;

    @Nested
    class TipDistance {

        @Test
        void armedHeadStandsJustPastTheBristlesOnTheThirdTick() {
            assertEquals(UrchinSpear.ARM_REACH,
                    UrchinSpear.tipDistance(UrchinSpear.ARM_TICKS, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void armingRisesFromNothing() {
            float early = UrchinSpear.tipDistance(1, 0f, STRIKE_TICK, LENGTH, REACH);
            float later = UrchinSpear.tipDistance(2, 0f, STRIKE_TICK, LENGTH, REACH);
            assertEquals(0f, UrchinSpear.tipDistance(0, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
            assertTrue(early > 0f && later > early && later < UrchinSpear.ARM_REACH, early + " then " + later);
        }

        @Test
        void tipStandsInTheMobOnTheTickTheDamageLands() {
            assertEquals(REACH, UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void firePunchesFasterAsItGoes() {
            float first = UrchinSpear.tipDistance(4, 0f, STRIKE_TICK, LENGTH, REACH)
                    - UrchinSpear.tipDistance(3, 0f, STRIKE_TICK, LENGTH, REACH);
            float last = UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, REACH)
                    - UrchinSpear.tipDistance(5, 0f, STRIKE_TICK, LENGTH, REACH);
            assertTrue(last > first, "fire's last tick " + last + " should outrun its first " + first);
        }

        @Test
        void retractTakesSixTicksAndPunchOutrunsIt() {
            float halfRetract = UrchinSpear.tipDistance(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH, REACH);
            assertEquals(REACH / 2, halfRetract, EPSILON);
            assertTrue(UrchinSpear.tipDistance(LENGTH - 1, 0.99f, STRIKE_TICK, LENGTH, REACH) < 0.01f);
            assertEquals(0f, UrchinSpear.tipDistance(LENGTH, 0f, STRIKE_TICK, LENGTH, REACH), EPSILON);
        }

        @Test
        void aMobInsideTheBristlesIsArmedAtNoFurtherThanItStands() {
            float close = 0.8f;
            assertEquals(close, UrchinSpear.tipDistance(UrchinSpear.ARM_TICKS, 0f, STRIKE_TICK, LENGTH, close),
                    EPSILON);
            assertEquals(close, UrchinSpear.tipDistance(STRIKE_TICK, 0f, STRIKE_TICK, LENGTH, close), EPSILON);
        }
    }

    /** The spines bristle with each shot and the urchin rests smooth between shots (operator ruling 2026-10-11). */
    @Nested
    class BristleShare {

        @Test
        void spinesRestRetractedBeforeAndAfterAShot() {
            assertEquals(0f, UrchinSpear.bristleShare(0, 0f, STRIKE_TICK, LENGTH), EPSILON);
            assertEquals(0f, UrchinSpear.bristleShare(LENGTH, 0f, STRIKE_TICK, LENGTH), EPSILON);
        }

        @Test
        void spinesStandFullyOutFromTheArmToTheStab() {
            for (int age = UrchinSpear.ARM_TICKS; age <= STRIKE_TICK; age++) {
                assertEquals(1f, UrchinSpear.bristleShare(age, 0f, STRIKE_TICK, LENGTH), EPSILON, "age " + age);
            }
        }

        @Test
        void spinesRetractAlongsideTheSpear() {
            float share = UrchinSpear.bristleShare(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH);
            assertEquals(0.5f, share, EPSILON);
            assertEquals(REACH * share, UrchinSpear.tipDistance(STRIKE_TICK + 3, 0f, STRIKE_TICK, LENGTH, REACH),
                    EPSILON);
        }
    }

    @Nested
    class RodEnds {

        @Test
        void aShortShaftKeepsEveryRodNestedAtItsEnd() {
            assertArrayEquals(new float[]{1f, 1f, 1f}, UrchinSpear.rodEnds(1f), EPSILON);
        }

        @Test
        void theHeadRodSlidesOutOfTheOthersFirst() {
            float shaft = UrchinSpear.ROD_LENGTH + 0.5f;
            assertArrayEquals(new float[]{UrchinSpear.ROD_LENGTH, UrchinSpear.ROD_LENGTH, shaft},
                    UrchinSpear.rodEnds(shaft), EPSILON);
        }

        @Test
        void aFullShaftSpreadsTheRodsEachSleevedInTheOneBehind() {
            float shaft = 2 * UrchinSpear.ROD_LENGTH + 0.5f;
            float[] ends = UrchinSpear.rodEnds(shaft);
            assertArrayEquals(new float[]{UrchinSpear.ROD_LENGTH, shaft - UrchinSpear.ROD_LENGTH, shaft}, ends,
                    EPSILON);
            for (int rod = 1; rod < ends.length; rod++) {
                float start = ends[rod] - UrchinSpear.ROD_LENGTH;
                assertTrue(start >= 0f && start <= ends[rod - 1], "rod " + rod + " starts outside the rod behind it");
            }
        }
    }
}

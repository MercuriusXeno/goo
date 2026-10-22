package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exact division of a GooValue and its two integer views, the melt floor and the
 * fabricate ceiling (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooValueArithmeticTest {

    @Nested
    class ExactDivision {

        @Test
        void oneGlassDividedBySixteenKeepsTheExactSixteenth() {
            GooValue share = goo(GooTypes.CRYSTAL, 1).divide(16);

            assertEquals(GooFraction.of(1, 16), share.exact(GooTypes.CRYSTAL));
        }

        @Test
        void sharesSummedBackMakeTheWhole() {
            GooValue share = goo(GooTypes.CRYSTAL, 6).divide(16);

            assertEquals(goo(GooTypes.CRYSTAL, 6), share.multiply(16));
        }
    }

    @Nested
    class IntegerViews {

        @Test
        void aThirdsAmountFloorsDownAndCeilsUp() {
            GooValue carpet = fraction(GooTypes.LEAF, 64, 3);

            assertEquals(21, carpet.meltFloor(GooTypes.LEAF));
            assertEquals(22, carpet.fabricateCeiling(GooTypes.LEAF));
        }

        @Test
        void aWholeAmountFloorsAndCeilsToItself() {
            GooValue whole = goo(GooTypes.LEAF, 21);

            assertEquals(21, whole.meltFloor(GooTypes.LEAF));
            assertEquals(21, whole.fabricateCeiling(GooTypes.LEAF));
        }
    }
}

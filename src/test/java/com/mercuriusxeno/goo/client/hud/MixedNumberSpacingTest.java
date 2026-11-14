package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.MixedNumber;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the mixed number spacing rule over glyph ink stated as the vanilla
 * font measures it: the gap after the whole, the numerator and denominator
 * one pixel off the diagonal divisor line's ink, and a whole or a fraction
 * under 1 laid out with no gap of its own.
 * mixed-number-gaps-its-whole-and-tightens-its-fraction
 */
class MixedNumberSpacingTest {

    private static final float EPSILON = 1e-6f;
    /** 21 and 1/3, a two-digit whole beside its fraction. */
    private static final MixedNumber TWENTY_ONE_AND_A_THIRD = GooFormat.mixedParts(GooFraction.of(64, 3));

    private static MixedNumberSpacing.Piece pieceOf(MixedNumberSpacing.Laid laid, String text) {
        return laid.pieces().stream().filter(piece -> piece.text().equals(text)).findFirst().orElseThrow();
    }

    private static float inkRightOf(MixedNumberSpacing.Laid laid, String text) {
        return pieceOf(laid, text).x() + StatedGlyphs.inkOf(text).inkRight();
    }

    private static float inkLeftOf(MixedNumberSpacing.Laid laid, String text) {
        return pieceOf(laid, text).x() + StatedGlyphs.inkOf(text).inkLeft();
    }

    @Nested
    class FractionSitsEvenlyAroundTheDivisorLine {

        @Test
        void numeratorAndDenominatorEachStandOnePixelFromTheLinesNearestInk() {
            for (int denominator = 2; denominator <= 12; denominator++) {
                for (int numerator = 1; numerator < denominator; numerator++) {
                    MixedNumber number = GooFormat.mixedParts(GooFraction.of(21L * denominator + numerator,
                            denominator));
                    MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(number, StatedGlyphs.VANILLA);
                    float divisorX = pieceOf(laid, MixedNumber.FRACTION_SLASH).x();
                    assertEquals(1f, StatedGlyphs.numeratorGap(number.numerator(),
                            pieceOf(laid, number.numerator()).x(), divisorX), EPSILON, "numerator of " + number.text());
                    assertEquals(1f, StatedGlyphs.denominatorGap(number.denominator(), divisorX,
                            pieceOf(laid, number.denominator()).x()), EPSILON, "denominator of " + number.text());
                }
            }
        }
    }

    @Nested
    class WholeStandsApartFromItsFraction {

        @Test
        void gapAfterTheWholeOutnumbersTheGapBetweenItsDigits() {
            MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(TWENTY_ONE_AND_A_THIRD, StatedGlyphs.VANILLA);
            MixedNumberSpacing.InkRun firstDigit = StatedGlyphs.inkOf("2");
            float betweenDigits = firstDigit.advance() - firstDigit.inkRight();
            float afterWhole = inkLeftOf(laid, "¹") - inkRightOf(laid, "21");
            assertTrue(afterWhole > betweenDigits, () -> afterWhole + " blank after the whole, "
                    + betweenDigits + " between its digits");
            assertEquals(2f, afterWhole, EPSILON);
        }
    }

    @Nested
    class NumberWithoutBothParts {

        @Test
        void wholeNumberIsItsDigitsAlone() {
            MixedNumber twentyTwo = GooFormat.mixedParts(GooFraction.of(22));
            MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(twentyTwo, StatedGlyphs.VANILLA);
            assertEquals(StatedGlyphs.inkOf("22").advance(), laid.width(), EPSILON);
            assertEquals(1, laid.pieces().size());
        }

        @Test
        void fractionUnderOneStartsAtItsNumerator() {
            MixedNumber third = GooFormat.mixedParts(GooFraction.of(1, 3));
            MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(third, StatedGlyphs.VANILLA);
            assertEquals(0f, inkLeftOf(laid, third.numerator()), EPSILON);
            assertEquals(third.numerator(), laid.pieces().getFirst().text());
        }
    }
}

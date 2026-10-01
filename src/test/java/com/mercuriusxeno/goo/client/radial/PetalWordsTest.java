package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where an ability petal's words sit: inside the petal, clear of the
 * icon and the type base, beside the icon on a petal at 3 o'clock and above
 * or below it on one at 12 o'clock (decision abilities-replace-the-hovered-type).
 */
class PetalWordsTest {

    private static final double ARC = 2.0 * Math.PI / 16;
    /** A wheel radius of 500 pixels: one pixel in normalized units. */
    private static final double PIXEL = 1.0 / 500;
    private static final PetalWords.Size ICON = new PetalWords.Size(18 * PIXEL, 18 * PIXEL);
    /** A wide word over two lines: 140 by 18 pixels. */
    private static final PetalWords.Size WORDS = new PetalWords.Size(140 * PIXEL, 18 * PIXEL);

    private static PetalMask.Petal centeredOn(double angle) {
        return new PetalMask.Petal(angle - ARC / 2, ARC, RadialWheel.HUB_FRACTION, 1.0);
    }

    private static PetalMask.Point place(PetalMask.Petal petal) {
        return PetalWords.place(petal, RadialWheel.TYPE_BASE_LENGTH, ICON, WORDS, PIXEL);
    }

    private static void assertFitsClearOfTheIcon(PetalMask.Petal petal, PetalMask.Point words) {
        assertEquals(0, PetalWords.cornersOutside(petal, RadialWheel.TYPE_BASE_LENGTH, words, WORDS));
        PetalMask.Point tip = petal.tipCenter();
        assertTrue(Math.abs(words.x() - tip.x()) >= (WORDS.width() + ICON.width()) / 2 - 1e-9
                || Math.abs(words.y() - tip.y()) >= (WORDS.height() + ICON.height()) / 2 - 1e-9);
    }

    @Test
    void atThreeOClockTheWordsSitBesideTheIcon() {
        PetalMask.Petal petal = centeredOn(Math.PI / 2);

        PetalMask.Point words = place(petal);

        assertFitsClearOfTheIcon(petal, words);
        assertTrue(words.x() < petal.tipCenter().x(), "toward the hub");
        assertEquals(petal.tipCenter().y(), words.y(), 1e-9);
    }

    @Test
    void atTwelveOClockTheSameWordsSlideUnderTheIcon() {
        PetalMask.Petal petal = centeredOn(0.0);

        PetalMask.Point words = place(petal);

        assertFitsClearOfTheIcon(petal, words);
        assertEquals(petal.tipCenter().x(), words.x(), 1e-9);
        assertTrue(words.y() > petal.tipCenter().y(), "below the icon, toward the hub");
    }
}

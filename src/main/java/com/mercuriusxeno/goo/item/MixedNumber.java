package com.mercuriusxeno.goo.item;

/**
 * A mixed number's three parts as each draws: the whole by magnitude, the
 * numerator in superscript digits and the denominator in subscript digits.
 * A whole number holds no fraction; a fraction under 1 holds no whole.
 * decision tooltip-and-plexer-hud-show-a-mixed-number
 *
 * @param whole       the whole's digits, empty for a fraction under 1
 * @param numerator   the numerator's superscript digits, empty for a whole number
 * @param denominator the denominator's subscript digits, empty for a whole number
 */
public record MixedNumber(String whole, String numerator, String denominator) {

    /** The fraction slash that sits between the small numerator and denominator. */
    public static final String FRACTION_SLASH = "⁄";

    /**
     * Whether the number holds a fraction to draw beside its whole.
     *
     * @return true unless the number is whole
     */
    public boolean hasFraction() {
        return !numerator.isEmpty();
    }

    /**
     * Whether the number holds a whole to draw before its fraction.
     *
     * @return true unless the number is a fraction under 1
     */
    public boolean hasWhole() {
        return !whole.isEmpty();
    }

    /**
     * The number as one string, its parts abutting: 64/3 reads "21¹⁄₃".
     *
     * @return the display string
     */
    public String text() {
        return hasFraction() ? whole + numerator + FRACTION_SLASH + denominator : whole;
    }
}

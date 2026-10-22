package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;

/**
 * Formats a raw goo amount by magnitude, K, M and B, with no unit word
 * (decision amounts-format-by-magnitude-alone). Shared between
 * server-side data components and client-side renderers.
 */
public final class GooFormat {

    /** Significant digits an amount of a thousand or more shows. */
    private static final int SIGNIFICANT_DIGITS = 3;
    /** Decimal scale multiplier for formatFraction. */
    private static final int DECIMAL_BASE = 10;
    /** Decimal point separator. */
    private static final String DOT = ".";
    /** Superscript digits 0 through 9, for a fraction's numerator. */
    private static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    /** Subscript digits 0 through 9, for a fraction's denominator. */
    private static final String SUBSCRIPT_DIGITS = "₀₁₂₃₄₅₆₇₈₉";
    /** The fraction slash that sits between the small numerator and denominator. */
    private static final String FRACTION_SLASH = "⁄";
    /** Separator between a container's total and its capacity. */
    private static final String OF_CAPACITY = " / ";

    /** Magnitudes in descending order; an amount under the smallest divisor reads whole. */
    private static final Magnitude[] MAGNITUDES = {
        new Magnitude(1_000_000_000L, "B"),
        new Magnitude(1_000_000L, "M"),
        new Magnitude(1_000L, "K"),
    };

    private static final long TICKS_PER_SECOND = 20;
    private static final long TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final long TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    private static final long TICKS_PER_DAY = TICKS_PER_HOUR * 24;
    private static final long TICKS_PER_YEAR = TICKS_PER_DAY * 365;
    private static final String SECONDS_SUFFIX = "s";

    /** Time units in descending order, each in ticks; a duration under a minute reads in whole seconds. */
    private static final Magnitude[] TIME_UNITS = {
        new Magnitude(TICKS_PER_YEAR, "y"),
        new Magnitude(TICKS_PER_DAY, "d"),
        new Magnitude(TICKS_PER_HOUR, "h"),
        new Magnitude(TICKS_PER_MINUTE, "m"),
    };

    /** A magnitude bracket: the divisor an amount at or past it is shown in, and its suffix. */
    private record Magnitude(long divisor, String suffix) {}

    private GooFormat() {}

    /**
     * Formats an amount by magnitude: 200 reads "200", 1200 reads "1.2K",
     * 16000 reads "16K", 32,000,000 reads "32M", 1,000,000,000 reads "1B".
     *
     * @param amount the raw amount
     * @return the display string
     */
    public static String formatAmount(long amount) {
        for (Magnitude magnitude : MAGNITUDES) {
            if (amount >= magnitude.divisor) {
                return formatSignificantDigits(amount, magnitude.divisor, magnitude.suffix);
            }
        }
        return Long.toString(amount);
    }

    /**
     * Formats an exact amount as a mixed number: a whole reads as formatAmount does,
     * a fraction under 1 reads alone, and any other reads its whole,
     * then the fraction with its numerator in superscript digits and its denominator in
     * subscript digits around a fraction slash, so 64/3 reads "21¹⁄₃" and 1/3 reads "¹⁄₃".
     * decision tooltip-and-plexer-hud-show-a-mixed-number
     *
     * @param amount the exact amount, never negative
     * @return the display string
     */
    public static String formatMixed(GooFraction amount) {
        int whole = amount.floor();
        GooFraction part = amount.minus(GooFraction.of(whole));
        if (part.isZero()) {
            return formatAmount(whole);
        }
        String fraction = toDigits(part.numerator(), SUPERSCRIPT_DIGITS) + FRACTION_SLASH
                + toDigits(part.denominator(), SUBSCRIPT_DIGITS);
        return whole == 0 ? fraction : formatAmount(whole) + fraction;
    }

    /**
     * Writes a number in the digit set given, superscript or subscript.
     *
     * @param value  the number, never negative
     * @param digits the ten digits 0 through 9 to write it in
     * @return the number in those digits
     */
    private static String toDigits(long value, String digits) {
        StringBuilder written = new StringBuilder();
        for (char digit : Long.toString(value).toCharArray()) {
            written.append(digits.charAt(digit - '0'));
        }
        return written.toString();
    }

    /**
     * Formats a duration in its largest unit of at least 1, years, days, hours or minutes, to
     * 3 significant digits truncated, or in whole seconds below a minute: 90 seconds reads
     * "1.5m", 2147687 seconds reads "24.8d", 250 ticks reads "12s".
     * counts-read-three-significant-digits
     *
     * @param ticks the duration in game ticks
     * @return the display string
     */
    public static String formatDuration(long ticks) {
        for (Magnitude unit : TIME_UNITS) {
            if (ticks >= unit.divisor) {
                return formatSignificantDigits(ticks, unit.divisor, unit.suffix);
            }
        }
        // the operator's ruling: below a minute seconds read whole, since ticks are too fine to matter
        return ticks / TICKS_PER_SECOND + SECONDS_SUFFIX;
    }

    /**
     * Formats a container's total against its capacity, each by magnitude:
     * 500 of 16000 reads "500 / 16K".
     *
     * @param total    the volume every type holds together
     * @param capacity the container's capacity
     * @return the display string
     */
    public static String formatFill(long total, long capacity) {
        return formatAmount(total) + OF_CAPACITY + formatAmount(capacity);
    }

    /**
     * Formats with three significant digits, truncated, trailing zeros trimmed.
     *
     * @param value   the raw amount
     * @param divisor the magnitude's divisor
     * @param suffix  the magnitude's suffix
     * @return the formatted string
     */
    private static String formatSignificantDigits(long value, long divisor, String suffix) {
        long whole = value / divisor;
        int decimalDigits = SIGNIFICANT_DIGITS - Long.toString(whole).length();
        long fraction = decimalDigits <= 0 ? 0 : scaleRemainder(value % divisor, divisor, decimalDigits);
        if (fraction == 0) {
            return whole + suffix;
        }
        return whole + DOT + padAndTrimZeros(Long.toString(fraction), decimalDigits) + suffix;
    }

    /** Scales the remainder by 10^digits and divides by the divisor.
     *
     * @param remainder the remainder after whole division
     * @param divisor   the magnitude's divisor
     * @param digits    the number of decimal digits
     * @return the scaled integer representing the decimal fraction
     */
    private static long scaleRemainder(long remainder, long divisor, int digits) {
        long scaled = remainder;
        for (int i = 0; i < digits; i++) { scaled *= DECIMAL_BASE; }
        return scaled / divisor;
    }

    /** Left-pads the raw digit string to the target width, then trims trailing zeros.
     *
     * @param raw    the raw digit string
     * @param digits the target width
     * @return the padded and trimmed string
     */
    private static String padAndTrimZeros(String raw, int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = raw.length(); i < digits; i++) { sb.append('0'); }
        sb.append(raw);
        int end = sb.length();
        while (end > 1 && sb.charAt(end - 1) == '0') { end--; }
        return sb.substring(0, end);
    }
}

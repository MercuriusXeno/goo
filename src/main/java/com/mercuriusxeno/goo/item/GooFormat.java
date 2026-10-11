package com.mercuriusxeno.goo.item;

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
    /** Separator between a container's total and its capacity. */
    private static final String OF_CAPACITY = " / ";

    /** Magnitudes in descending order; an amount under the smallest divisor reads whole. */
    private static final Magnitude[] MAGNITUDES = {
        new Magnitude(1_000_000_000L, "B"),
        new Magnitude(1_000_000L, "M"),
        new Magnitude(1_000L, "K"),
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

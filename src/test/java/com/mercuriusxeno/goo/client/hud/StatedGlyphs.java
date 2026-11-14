package com.mercuriusxeno.goo.client.hud;

import java.util.List;

/**
 * Glyph advances and ink stated as the vanilla font measures them, so the
 * spacing rule runs with no Font: a whole digit advances 6 pixels, a
 * superscript or subscript digit 5 and its 1 advances 4, the divisor line is
 * unifont's half-scale diagonal slash advancing 4, and each small digit inks
 * the five rows its shape below draws.
 */
public final class StatedGlyphs {

    /** The slash's ink rows, top right to bottom left, read off unifont's U+2044 at half scale. */
    public static final List<MixedNumberSpacing.RowSpan> SLASH_ROWS = List.of(
            new MixedNumberSpacing.RowSpan(2, 2.5f, 3f),
            new MixedNumberSpacing.RowSpan(3, 1.5f, 2.5f),
            new MixedNumberSpacing.RowSpan(4, 1f, 2f),
            new MixedNumberSpacing.RowSpan(5, 0.5f, 1.5f),
            new MixedNumberSpacing.RowSpan(6, 0f, 0.5f));
    /** The vanilla font's ink, its advances stated here, as the rule reads it. */
    public static final MixedNumberSpacing.Glyphs VANILLA = MixedNumberSpacing.glyphsOf(StatedGlyphs::widthOf);

    /** Each small digit's five ink rows as the vanilla sheets draw it, superscript and subscript alike. */
    private static final String[][] SMALL_DIGIT_ROWS = {
        {".##.", "#..#", "#..#", "#..#", ".##."},
        {".#..", "##..", ".#..", ".#..", "###."},
        {".##.", "#..#", "..#.", ".#..", "####"},
        {".##.", "#..#", "..#.", "#..#", ".##."},
        {"..##", ".#.#", "#..#", "####", "...#"},
        {"####", "#...", "###.", "...#", "###."},
        {".##.", "#...", "###.", "#..#", ".##."},
        {"####", "#..#", "..#.", ".#..", ".#.."},
        {".##.", "#..#", ".##.", "#..#", ".##."},
        {".##.", "#..#", ".###", "...#", ".##."},
    };
    private static final String SUPERSCRIPT = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUBSCRIPT = "₀₁₂₃₄₅₆₇₈₉";
    /** The row superscript digits' ink starts on. */
    private static final int RAISED_TOP = 0;
    /** The row subscript digits' ink starts on. */
    private static final int LOWERED_TOP = 3;
    private static final int WHOLE_DIGIT = 6;
    private static final int SMALL_DIGIT = 5;
    private static final int SMALL_ONE = 4;
    private static final int SLASH_ADVANCE = 4;

    private StatedGlyphs() {
    }

    /**
     * A run's width as the vanilla font measures it.
     *
     * @param text whole, superscript or subscript digits, or the slash
     * @return the run's advance in font pixels
     */
    public static int widthOf(String text) {
        return text.chars().map(StatedGlyphs::advanceOf).sum();
    }

    /**
     * A run's advance and the box of its ink, which starts at 0 and ends one column before its advance.
     *
     * @param text whole, superscript or subscript digits
     * @return the run's ink box
     */
    public static MixedNumberSpacing.InkRun inkOf(String text) {
        float advance = widthOf(text);
        return new MixedNumberSpacing.InkRun(advance, 0f, advance - 1f, List.of());
    }

    /**
     * The nearest blank pixels between the numerator's ink and the divisor
     * line's, read row by row over the rows both ink.
     *
     * @param numerator  the numerator's superscript digits
     * @param numeratorX the numerator's origin
     * @param divisorX   the divisor line's origin
     * @return the narrowest gap over any shared row
     */
    public static float numeratorGap(String numerator, float numeratorX, float divisorX) {
        float lastX = numeratorX + widthOf(numerator.substring(0, numerator.length() - 1));
        String[] shape = SMALL_DIGIT_ROWS[SUPERSCRIPT.indexOf(numerator.charAt(numerator.length() - 1))];
        float nearest = Float.MAX_VALUE;
        for (MixedNumberSpacing.RowSpan span : SLASH_ROWS) {
            int row = span.row() - RAISED_TOP;
            if (row >= 0 && row < shape.length) {
                nearest = Math.min(nearest, divisorX + span.left() - (lastX + shape[row].lastIndexOf('#') + 1));
            }
        }
        return nearest;
    }

    /**
     * The nearest blank pixels between the divisor line's ink and the
     * denominator's, read row by row over the rows both ink.
     *
     * @param denominator  the denominator's subscript digits
     * @param divisorX     the divisor line's origin
     * @param denominatorX the denominator's origin
     * @return the narrowest gap over any shared row
     */
    public static float denominatorGap(String denominator, float divisorX, float denominatorX) {
        String[] shape = SMALL_DIGIT_ROWS[SUBSCRIPT.indexOf(denominator.charAt(0))];
        float nearest = Float.MAX_VALUE;
        for (MixedNumberSpacing.RowSpan span : SLASH_ROWS) {
            int row = span.row() - LOWERED_TOP;
            if (row >= 0 && row < shape.length) {
                nearest = Math.min(nearest, denominatorX + shape[row].indexOf('#') - (divisorX + span.right()));
            }
        }
        return nearest;
    }

    private static int advanceOf(int glyph) {
        if (glyph == '⁄') {
            return SLASH_ADVANCE;
        }
        if (glyph == '¹' || glyph == '₁') {
            return SMALL_ONE;
        }
        return glyph < '0' + 10 ? WHOLE_DIGIT : SMALL_DIGIT;
    }
}

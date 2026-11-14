package com.mercuriusxeno.goo.client.hud;

import java.util.List;

/**
 * Glyph ink stated as the vanilla font measures it, so the spacing rule runs
 * with no Font: a whole digit advances 6 pixels, a superscript or subscript
 * digit 5 and its 1 advances 4, every digit's ink starts at its first column
 * and ends one blank column before its advance, and the divisor line is
 * unifont's half-scale diagonal slash, 4 pixels of advance.
 */
public final class StatedGlyphs {

    /** The slash's ink rows, top right to bottom left, read off unifont's U+2044 at half scale. */
    public static final MixedNumberSpacing.DivisorInk SLASH = new MixedNumberSpacing.DivisorInk(4f, List.of(
            new MixedNumberSpacing.RowSpan(2, 2.5f, 3f),
            new MixedNumberSpacing.RowSpan(3, 1.5f, 2.5f),
            new MixedNumberSpacing.RowSpan(4, 1f, 2f),
            new MixedNumberSpacing.RowSpan(5, 0.5f, 1.5f),
            new MixedNumberSpacing.RowSpan(6, 0f, 0.5f)));
    /** Superscript digits ink rows 0 through 4. */
    public static final MixedNumberSpacing.RowBand RAISED = new MixedNumberSpacing.RowBand(0, 5);
    /** Subscript digits ink rows 3 through 7. */
    public static final MixedNumberSpacing.RowBand LOWERED = new MixedNumberSpacing.RowBand(3, 8);
    /** The vanilla font's ink as stated here. */
    public static final MixedNumberSpacing.Glyphs VANILLA =
            new MixedNumberSpacing.Glyphs(StatedGlyphs::inkOf, SLASH, RAISED, LOWERED);

    private static final float WHOLE_DIGIT = 6f;
    private static final float SMALL_DIGIT = 5f;
    private static final float SMALL_ONE = 4f;

    private StatedGlyphs() {
    }

    /**
     * A run's ink as the vanilla font lays it.
     *
     * @param text whole, superscript or subscript digits
     * @return its advance, its ink starting at 0 and ending one column before its advance
     */
    public static MixedNumberSpacing.InkRun inkOf(String text) {
        float advance = (float) text.chars().mapToDouble(StatedGlyphs::advanceOf).sum();
        return new MixedNumberSpacing.InkRun(advance, 0f, advance - 1f);
    }

    private static double advanceOf(int glyph) {
        if (glyph == '¹' || glyph == '₁') {
            return SMALL_ONE;
        }
        return glyph < '0' + 10 ? WHOLE_DIGIT : SMALL_DIGIT;
    }
}

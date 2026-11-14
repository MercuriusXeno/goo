package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.MixedNumber;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The one spacing rule a mixed number draws by, in font pixels: a small gap
 * between the whole and the numerator, and the numerator and denominator
 * tight against the divisor line, measured to the line's ink over the rows
 * each number covers since the line runs diagonally.
 * mixed-number-gaps-its-whole-and-tightens-its-fraction
 */
public final class MixedNumberSpacing {

    /** Blank pixels between the whole's ink and the numerator's, one more than between two digits. */
    private static final float WHOLE_GAP = 2f;
    /** Blank pixels between each small number's ink and the divisor line's. */
    private static final float DIVISOR_GAP = 1f;
    /** The blank column a bitmap glyph's advance carries past its ink. */
    private static final float BITMAP_TRAILING_BLANK = 1f;
    /**
     * The fraction slash's ink in font pixel rows, top right to bottom left: no
     * vanilla bitmap sheet holds U+2044, so unifont draws it at half scale,
     * cropped to its ink, its 16 rows over the line's 8.
     */
    private static final List<RowSpan> SLASH_INK = List.of(
            new RowSpan(2, 2.5f, 3f),
            new RowSpan(3, 1.5f, 2.5f),
            new RowSpan(4, 1f, 2f),
            new RowSpan(5, 0.5f, 1.5f),
            new RowSpan(6, 0f, 0.5f));
    /** The vanilla superscript digits ink rows 0 through 4. */
    private static final RowBand RAISED_ROWS = new RowBand(0, 5);
    /** The vanilla subscript digits ink rows 3 through 7. */
    private static final RowBand LOWERED_ROWS = new RowBand(3, 8);

    private MixedNumberSpacing() {
    }

    /**
     * A run of glyphs' advance and the edges of its ink, in font pixels from
     * the run's origin.
     *
     * @param advance  how far the run moves the pen
     * @param inkLeft  the left edge of the run's first ink column
     * @param inkRight the right edge of the run's last ink column
     */
    public record InkRun(float advance, float inkLeft, float inkRight) {
    }

    /**
     * One font pixel row of the divisor line's ink, its edges from the line's origin.
     *
     * @param row   the row, in font pixels down from the line's top
     * @param left  the ink's left edge
     * @param right the ink's right edge
     */
    public record RowSpan(int row, float left, float right) {
    }

    /**
     * The rows a run of digits covers, top inclusive and bottom exclusive.
     *
     * @param top    the first row holding the run's ink
     * @param bottom the row under the run's last ink row
     */
    public record RowBand(int top, int bottom) {

        boolean holds(int row) {
            return row >= top && row < bottom;
        }
    }

    /**
     * The divisor line's advance and its ink row by row.
     *
     * @param advance how far the line moves the pen
     * @param rows    the line's ink, one span per inked row
     */
    public record DivisorInk(float advance, List<RowSpan> rows) {

        /**
         * The leftmost ink over the rows given.
         *
         * @param band the rows to read
         * @return the left edge of the leftmost ink there
         */
        float leftOver(RowBand band) {
            return (float) rows.stream().filter(span -> band.holds(span.row()))
                    .mapToDouble(RowSpan::left).min().orElse(0.0);
        }

        /**
         * The rightmost ink over the rows given.
         *
         * @param band the rows to read
         * @return the right edge of the rightmost ink there
         */
        float rightOver(RowBand band) {
            return (float) rows.stream().filter(span -> band.holds(span.row()))
                    .mapToDouble(RowSpan::right).max().orElse(advance);
        }
    }

    /**
     * What the rule reads of a font: each run's ink, the divisor line's ink and
     * the rows the raised numerator and the lowered denominator cover.
     *
     * @param inkOf            the ink of a run of whole, numerator or denominator digits
     * @param divisor          the divisor line's ink
     * @param numeratorRows    the rows the superscript numerator covers
     * @param denominatorRows  the rows the subscript denominator covers
     */
    public record Glyphs(Function<String, InkRun> inkOf, DivisorInk divisor, RowBand numeratorRows,
                         RowBand denominatorRows) {
    }

    /**
     * One part of a laid mixed number and where it draws.
     *
     * @param text the part's text
     * @param x    its origin, in font pixels from the number's left edge
     */
    public record Piece(String text, float x) {
    }

    /**
     * A mixed number laid out: its parts, each at its origin, and its width.
     *
     * @param pieces the whole, numerator, divisor line and denominator that draw
     * @param width  the number's width in font pixels
     */
    public record Laid(List<Piece> pieces, float width) {
    }

    /**
     * Lays a mixed number out by the spacing rule.
     *
     * @param number the parts to lay out
     * @param glyphs the ink of the font the parts draw in
     * @return each part's origin and the number's width
     */
    public static Laid lay(MixedNumber number, Glyphs glyphs) {
        if (!number.hasFraction()) {
            return new Laid(List.of(new Piece(number.whole(), 0f)), glyphs.inkOf().apply(number.whole()).advance());
        }
        List<Piece> pieces = new ArrayList<>();
        InkRun numerator = glyphs.inkOf().apply(number.numerator());
        float numeratorX = -numerator.inkLeft();
        if (number.hasWhole()) {
            pieces.add(new Piece(number.whole(), 0f));
            numeratorX += glyphs.inkOf().apply(number.whole()).inkRight() + WHOLE_GAP;
        }
        pieces.add(new Piece(number.numerator(), numeratorX));
        DivisorInk divisor = glyphs.divisor();
        float divisorX = numeratorX + numerator.inkRight() + DIVISOR_GAP - divisor.leftOver(glyphs.numeratorRows());
        pieces.add(new Piece(MixedNumber.FRACTION_SLASH, divisorX));
        InkRun denominator = glyphs.inkOf().apply(number.denominator());
        float denominatorX = divisorX + divisor.rightOver(glyphs.denominatorRows()) + DIVISOR_GAP
                - denominator.inkLeft();
        pieces.add(new Piece(number.denominator(), denominatorX));
        return new Laid(List.copyOf(pieces), denominatorX + denominator.advance());
    }

    /**
     * Lays a mixed number out in a font.
     *
     * @param number the parts to lay out
     * @param font   the font the parts draw in
     * @return each part's origin and the number's width
     */
    public static Laid lay(MixedNumber number, Font font) {
        return lay(number, glyphsOf(font));
    }

    /**
     * The glyph ink the rule reads of a font.
     *
     * @param font the font the parts draw in
     * @return the font's ink as the rule reads it
     */
    public static Glyphs glyphsOf(Font font) {
        return new Glyphs(text -> bitmapRunOf(font.width(text)),
                new DivisorInk(font.width(MixedNumber.FRACTION_SLASH), SLASH_INK), RAISED_ROWS, LOWERED_ROWS);
    }

    /**
     * A bitmap glyph run's ink: the vanilla sheets ink every digit and magnitude
     * letter from its cell's first column, and a bitmap glyph advances one blank
     * column past its ink.
     *
     * @param advance the run's width as the font measures it
     * @return the run's ink
     */
    private static InkRun bitmapRunOf(float advance) {
        return new InkRun(advance, 0f, advance - BITMAP_TRAILING_BLANK);
    }
}

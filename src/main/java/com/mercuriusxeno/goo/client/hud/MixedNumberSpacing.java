package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.MixedNumber;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The one spacing rule a mixed number draws by, in font pixels: a small gap
 * between the whole and the numerator, and the numerator and denominator
 * each one pixel off the divisor line's nearest ink, read row by row since
 * the line runs diagonally and each digit's ink edge differs by row.
 * mixed-number-gaps-its-whole-and-tightens-its-fraction
 */
public final class MixedNumberSpacing {

    /** Blank pixels between the whole's ink and the numerator's, one more than between two digits. */
    private static final float WHOLE_GAP = 2f;
    /** Blank pixels between each small number's nearest ink and the divisor line's. */
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
    /** The row the vanilla superscript digits' ink starts on. */
    private static final int RAISED_TOP = 0;
    /** The row the vanilla subscript digits' ink starts on. */
    private static final int LOWERED_TOP = 3;
    private static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUBSCRIPT_DIGITS = "₀₁₂₃₄₅₆₇₈₉";
    private static final char INK = '#';
    /**
     * Each small digit's five ink rows as the vanilla sheets draw it, the
     * superscript and subscript digits alike.
     */
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

    private MixedNumberSpacing() {
    }

    /**
     * A run of glyphs' advance and its ink, in font pixels from the run's origin.
     *
     * @param advance  how far the run moves the pen
     * @param inkLeft  the left edge of the run's first ink column
     * @param inkRight the right edge of the run's last ink column
     * @param rows     the run's ink row by row, empty where only its box is known
     */
    public record InkRun(float advance, float inkLeft, float inkRight, List<RowSpan> rows) {
    }

    /**
     * One font pixel row of a glyph run's ink, its edges from the run's origin.
     *
     * @param row   the row, in font pixels down from the line's top
     * @param left  the ink's left edge
     * @param right the ink's right edge
     */
    public record RowSpan(int row, float left, float right) {
    }

    /**
     * What the rule reads of a font: each run's ink and the divisor line's.
     *
     * @param inkOf   the ink of a run of whole, numerator or denominator digits
     * @param divisor the divisor line's ink
     */
    public record Glyphs(Function<String, InkRun> inkOf, InkRun divisor) {
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
        float divisorX = numeratorX + overhang(numerator, glyphs.divisor()) + DIVISOR_GAP;
        pieces.add(new Piece(MixedNumber.FRACTION_SLASH, divisorX));
        InkRun denominator = glyphs.inkOf().apply(number.denominator());
        float denominatorX = divisorX + overhang(glyphs.divisor(), denominator) + DIVISOR_GAP;
        pieces.add(new Piece(number.denominator(), denominatorX));
        return new Laid(List.copyOf(pieces), denominatorX + denominator.advance());
    }

    /**
     * How far right of the right run's origin the left run's ink reaches, read
     * over the rows both ink, so a run set this far plus a gap past the left
     * one's origin leaves that gap at their nearest row; runs sharing no row
     * read by their boxes.
     *
     * @param left  the run on the left
     * @param right the run on the right
     * @return the widest reach of the left run's ink past the right run's, over a shared row
     */
    private static float overhang(InkRun left, InkRun right) {
        float widest = Float.NEGATIVE_INFINITY;
        for (RowSpan leftRow : left.rows()) {
            for (RowSpan rightRow : right.rows()) {
                if (leftRow.row() == rightRow.row()) {
                    widest = Math.max(widest, leftRow.right() - rightRow.left());
                }
            }
        }
        return widest == Float.NEGATIVE_INFINITY ? left.inkRight() - right.inkLeft() : widest;
    }

    /**
     * Lays a mixed number out in a font.
     *
     * @param number the parts to lay out
     * @param font   the font the parts draw in
     * @return each part's origin and the number's width
     */
    public static Laid lay(MixedNumber number, Font font) {
        return lay(number, glyphsOf(font::width));
    }

    /**
     * The glyph ink the rule reads of a font: its advances as it measures them,
     * the vanilla small digits' ink row by row, and unifont's slash.
     *
     * @param widthOf the width in pixels the font gives a string
     * @return the font's ink as the rule reads it
     */
    public static Glyphs glyphsOf(ToIntFunction<String> widthOf) {
        float slashAdvance = widthOf.applyAsInt(MixedNumber.FRACTION_SLASH);
        return new Glyphs(text -> runOf(text, widthOf),
                new InkRun(slashAdvance, 0f, slashAdvance - BITMAP_TRAILING_BLANK, SLASH_INK));
    }

    /**
     * A run's ink: the vanilla sheets ink every digit and magnitude letter from
     * its cell's first column, a bitmap glyph advances one blank column past its
     * ink, and a small digit's rows read from its shape.
     *
     * @param text    the run
     * @param widthOf the width in pixels the font gives a string
     * @return the run's ink
     */
    private static InkRun runOf(String text, ToIntFunction<String> widthOf) {
        float advance = widthOf.applyAsInt(text);
        List<RowSpan> rows = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            addSmallDigitRows(rows, text.charAt(index), widthOf.applyAsInt(text.substring(0, index)));
        }
        return new InkRun(advance, 0f, advance - BITMAP_TRAILING_BLANK, List.copyOf(rows));
    }

    private static void addSmallDigitRows(List<RowSpan> rows, char glyph, float glyphX) {
        int raised = SUPERSCRIPT_DIGITS.indexOf(glyph);
        int lowered = SUBSCRIPT_DIGITS.indexOf(glyph);
        int digit = Math.max(raised, lowered);
        if (digit < 0) {
            return;
        }
        int top = raised >= 0 ? RAISED_TOP : LOWERED_TOP;
        String[] shape = SMALL_DIGIT_ROWS[digit];
        for (int row = 0; row < shape.length; row++) {
            rows.add(new RowSpan(top + row, glyphX + shape[row].indexOf(INK), glyphX + shape[row].lastIndexOf(INK) + 1));
        }
    }
}

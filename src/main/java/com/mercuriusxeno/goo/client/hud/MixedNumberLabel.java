package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.MixedNumber;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A line of text laid out with each mixed number in it spaced by the spacing
 * rule, the rest of the text measured as the font measures it: a cost label
 * such as "4⁴⁄₉ x r³" draws its whole, numerator, divisor line and
 * denominator at the offsets MixedNumberSpacing.lay answers, and measures as
 * wide as that respacing makes it. A line holding no mixed number is one
 * piece, drawn and measured as the font draws and measures it.
 * every-mixed-number-takes-the-spacing-rule
 *
 * @param text   the line as one string
 * @param pieces each run of the line and its origin, in font pixels from the line's left edge
 * @param width  the line's width in font pixels
 */
public record MixedNumberLabel(String text, List<MixedNumberSpacing.Piece> pieces, float width) {

    /**
     * A mixed number as GooFormat.formatMixed writes it: a whole by magnitude,
     * when one stands, then superscript digits, the fraction slash and
     * subscript digits.
     */
    private static final Pattern MIXED_NUMBER = Pattern.compile(
            "(\\d+(?:\\.\\d+)?[KMB]?)?([⁰¹²³⁴⁵⁶⁷⁸⁹]+)" + MixedNumber.FRACTION_SLASH + "([₀₁₂₃₄₅₆₇₈₉]+)");
    private static final String NO_WHOLE = "";
    private static final int WHOLE = 1;
    private static final int NUMERATOR = 2;
    private static final int DENOMINATOR = 3;

    /**
     * Copies the pieces so the label holds its own list.
     *
     * @param text   the line as one string
     * @param pieces each run of the line and its origin, in font pixels from the line's left edge
     * @param width  the line's width in font pixels
     */
    public MixedNumberLabel {
        pieces = List.copyOf(pieces);
    }

    /**
     * Lays a line out in a font.
     *
     * @param text the line
     * @param font the font it draws in
     * @return the line's pieces and width
     */
    public static MixedNumberLabel lay(String text, Font font) {
        return lay(text, font::width);
    }

    /**
     * Lays a line out: each mixed number in it by the spacing rule, each run
     * between them at the width the font gives it.
     *
     * @param text    the line
     * @param widthOf the width in pixels the font gives a string
     * @return the line's pieces and width
     */
    public static MixedNumberLabel lay(String text, ToIntFunction<String> widthOf) {
        MixedNumberSpacing.Glyphs glyphs = MixedNumberSpacing.glyphsOf(widthOf);
        List<MixedNumberSpacing.Piece> pieces = new ArrayList<>();
        Matcher number = MIXED_NUMBER.matcher(text);
        float x = 0f;
        int plainStart = 0;
        while (number.find()) {
            x = addPlain(pieces, text.substring(plainStart, number.start()), x, widthOf);
            String whole = number.group(WHOLE);
            MixedNumberSpacing.Laid laid = MixedNumberSpacing.lay(new MixedNumber(whole == null ? NO_WHOLE : whole,
                    number.group(NUMERATOR), number.group(DENOMINATOR)), glyphs);
            for (MixedNumberSpacing.Piece piece : laid.pieces()) {
                pieces.add(new MixedNumberSpacing.Piece(piece.text(), x + piece.x()));
            }
            x += laid.width();
            plainStart = number.end();
        }
        x = addPlain(pieces, text.substring(plainStart), x, widthOf);
        return new MixedNumberLabel(text, pieces, x);
    }

    private static float addPlain(List<MixedNumberSpacing.Piece> pieces, String run, float x,
                                  ToIntFunction<String> widthOf) {
        if (run.isEmpty()) {
            return x;
        }
        pieces.add(new MixedNumberSpacing.Piece(run, x));
        return x + widthOf.applyAsInt(run);
    }

    /**
     * A width measure answering each string's respaced width, so a panel
     * sizing its rows by it sizes them to the spacing rule.
     *
     * @param widthOf the width in pixels the font gives a string
     * @return the width in whole pixels each string takes once laid out, rounded up
     */
    public static ToIntFunction<String> spacedWidth(ToIntFunction<String> widthOf) {
        return text -> lay(text, widthOf).fullWidth();
    }

    /**
     * Whether the line holds a mixed number the spacing rule laid apart.
     *
     * @return true unless the line is one plain run
     */
    public boolean holdsMixedNumber() {
        return pieces.size() > 1;
    }

    /**
     * The line's width in whole pixels, rounded up, as a pixel layout sizes to it.
     *
     * @return the width rounded up
     */
    public int fullWidth() {
        return (int) Math.ceil(width);
    }

    /**
     * Draws the line on screen, its top left at the point given: a plain line
     * as the font draws it, a line holding a mixed number run by run, each at
     * its origin.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param x        the line's left edge
     * @param y        the line's top
     * @param color    the ARGB color
     * @param shadow   whether the font draws its drop shadow
     */
    public void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int color, boolean shadow) {
        if (!holdsMixedNumber()) {
            graphics.text(font, text, x, y, color, shadow);
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        for (MixedNumberSpacing.Piece piece : pieces) {
            pose.pushMatrix();
            pose.translate(x + piece.x(), y);
            graphics.text(font, piece.text(), 0, 0, color, shadow);
            pose.popMatrix();
        }
    }
}

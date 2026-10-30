package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How the manager reads and draws a fraction: an improper amount reduced to its whole and a part under 1, the whole at
 * full size and the part at half scale as its numerator over a bar over its denominator, each number centered on the
 * bar, as recipe counts and goo amounts both draw (operator ruling: 81/4 reads 20 and 1 over 4, never 81/4).
 */
final class StackedFractions {
    /** A fraction of no whole and no part, as a count equal to 1 reads. */
    static final Mixed NONE = new Mixed("", "", "");
    /** The bar's thickness in half-scale space, one screen pixel. */
    static final int BAR_THICKNESS = 2;
    /** A stack's height in half-scale space, numerator ink top to denominator ink bottom. */
    static final int STACK_HEIGHT = 18;
    private static final float SCALE = 0.5f;
    // Half scale doubles every coordinate the stack draws at.
    private static final int SPACE = 2;
    // A digit's ink rows under its text top.
    private static final int INK_HEIGHT = 7;
    private static final int BAR_TOP = INK_HEIGHT + 1;
    private static final int DENOMINATOR_TOP = BAR_TOP + BAR_THICKNESS + 1;
    // The room between a whole and its stack, in screen pixels.
    private static final int WHOLE_GAP = 1;
    // A full-scale glyph's ink centre under its text top, in half-scale space.
    private static final int GLYPH_CENTRE = INK_HEIGHT;
    private static final char DIVIDER = '/';
    private static final String NO_TEXT = "";

    private StackedFractions() {
    }

    /**
     * An amount as a mixed number.
     *
     * @param whole       its whole part, empty where it is under 1
     * @param numerator   its part under 1's numerator, empty where it is whole
     * @param denominator its part under 1's denominator, empty where it is whole
     */
    record Mixed(String whole, String numerator, String denominator) {
        /**
         * @return whether it holds a part under 1, which draws as a stack
         */
        boolean stacked() {
            return !numerator.isEmpty();
        }
    }

    /**
     * Where a stack's parts stand, in half-scale space.
     *
     * @param numeratorX   the numerator's left edge
     * @param numeratorY   the numerator's top edge
     * @param barX         the bar's left edge
     * @param barY         the bar's top edge
     * @param barWidth     the bar's width, the wider number's
     * @param denominatorX the denominator's left edge
     * @param denominatorY the denominator's top edge
     */
    record Stack(int numeratorX, int numeratorY, int barX, int barY, int barWidth, int denominatorX,
                 int denominatorY) {
    }

    /**
     * @param amount an amount as the server writes it: a whole number, a reduced n/d, or any other text
     * @return the amount reduced to its whole and its part under 1; text that is no fraction reads whole alone
     */
    static Mixed of(String amount) {
        int divider = amount.indexOf(DIVIDER);
        if (divider < 0) {
            return new Mixed(amount, NO_TEXT, NO_TEXT);
        }
        try {
            long numerator = Long.parseLong(amount.substring(0, divider).trim());
            long denominator = Long.parseLong(amount.substring(divider + 1).trim());
            return denominator <= 0 ? new Mixed(amount, NO_TEXT, NO_TEXT) : of(numerator, denominator);
        } catch (NumberFormatException e) {
            return new Mixed(amount, NO_TEXT, NO_TEXT);
        }
    }

    /**
     * @param numerator   a fraction's numerator, at least 0
     * @param denominator its denominator, above 0
     * @return the fraction reduced to its whole and its part under 1, the whole empty where it is 0
     */
    static Mixed of(long numerator, long denominator) {
        long divisor = greatestCommonDivisor(numerator, denominator);
        long top = numerator / divisor;
        long bottom = denominator / divisor;
        long whole = top / bottom;
        long part = top % bottom;
        String wholeText = whole == 0 && part != 0 ? NO_TEXT : String.valueOf(whole);
        return part == 0 ? new Mixed(wholeText, NO_TEXT, NO_TEXT)
                : new Mixed(wholeText, String.valueOf(part), String.valueOf(bottom));
    }

    /**
     * @param left             the stack's left edge, in half-scale space
     * @param top              the numerator's ink top, in half-scale space
     * @param numeratorWidth   the numerator's width at full scale
     * @param denominatorWidth the denominator's width at full scale
     * @return the numerator above a bar as wide as the wider number and the denominator below it, each centered
     */
    static Stack layStack(int left, int top, int numeratorWidth, int denominatorWidth) {
        int barWidth = Math.max(numeratorWidth, denominatorWidth);
        return new Stack(left + (barWidth - numeratorWidth) / SPACE, top, left, top + BAR_TOP, barWidth,
                left + (barWidth - denominatorWidth) / SPACE, top + DENOMINATOR_TOP);
    }

    /**
     * @param font   the font
     * @param amount an amount as a mixed number
     * @return the width it draws at: the whole at full size, a gap, and the stack at half scale
     */
    static int width(Font font, Mixed amount) {
        int whole = amount.whole().isEmpty() ? 0 : font.width(amount.whole());
        if (!amount.stacked()) {
            return whole;
        }
        int stack = halfScale(Math.max(font.width(amount.numerator()), font.width(amount.denominator())));
        return whole == 0 ? stack : whole + WHOLE_GAP + stack;
    }

    /**
     * Draws an amount on a text line: its whole at full size, then its part under 1 as a half-scale stack centered on
     * the line's glyphs.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param amount   the amount as the server writes it
     * @param x        its left edge
     * @param textTop  the line's text top
     * @param color    its color
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, String amount, int x, int textTop, int color) {
        Mixed mixed = of(amount);
        int stackLeft = x;
        if (!mixed.whole().isEmpty()) {
            graphics.text(font, mixed.whole(), x, textTop, color);
            stackLeft += font.width(mixed.whole()) + WHOLE_GAP;
        }
        if (mixed.stacked()) {
            int top = textTop * SPACE + GLYPH_CENTRE - STACK_HEIGHT / SPACE;
            drawStack(graphics, font, mixed, layStack(stackLeft * SPACE, top, font.width(mixed.numerator()),
                    font.width(mixed.denominator())), color);
        }
    }

    /**
     * @param graphics the frame's graphics
     * @param font     the font
     * @param amount   the amount whose part under 1 draws
     * @param stack    where the stack's parts stand, in half-scale space
     * @param color    its color
     */
    static void drawStack(GuiGraphicsExtractor graphics, Font font, Mixed amount, Stack stack, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(SCALE, SCALE);
        graphics.text(font, amount.numerator(), stack.numeratorX(), stack.numeratorY(), color);
        graphics.fill(stack.barX(), stack.barY(), stack.barX() + stack.barWidth(), stack.barY() + BAR_THICKNESS,
                color);
        graphics.text(font, amount.denominator(), stack.denominatorX(), stack.denominatorY(), color);
        graphics.pose().popMatrix();
    }

    private static int halfScale(int width) {
        return (width + 1) / SPACE;
    }

    private static long greatestCommonDivisor(long a, long b) {
        long x = Math.abs(a);
        long y = Math.abs(b);
        while (y != 0) {
            long remainder = x % y;
            x = y;
            y = remainder;
        }
        return x == 0 ? 1 : x;
    }
}

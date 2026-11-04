package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * A font drawn at a scale: every width it answers and every text it draws are the font's at that scale, so the
 * glyphs and the room between them shrink together, as the expression builder letters its words (decision
 * builder-font-and-spacing-shrink-padding-untouched). At scale one it is the font itself and draws with no pose.
 *
 * @param font  the font
 * @param scale how large its glyphs draw against the font's own
 */
record Lettering(Font font, float scale) {
    private static final float FULL = 1f;
    // A font's line holds an empty row under its glyphs, so centering the ink centers one row lower than the line.
    private static final int GLYPH_GAP = 1;
    private static final int HALF = 2;
    // The font advances one pixel past each glyph's ink, so a run's width ends a pixel past its last glyph.
    private static final int TRAILING_ADVANCE = 1;
    // A line's last row holds its glyphs' shadow, under their ink.
    private static final int SHADOW_ROW = 1;

    /**
     * @param font the font
     * @return the font at its own size
     */
    static Lettering full(Font font) {
        return new Lettering(font, FULL);
    }

    /**
     * @return whether the lettering draws at the font's own size
     */
    boolean isFull() {
        return scale == FULL;
    }

    /**
     * @param text words
     * @return the width they take at this scale, rounded up to a whole pixel
     */
    int width(String text) {
        return isFull() ? font.width(text) : (int) Math.ceil(font.width(text) * scale);
    }

    /**
     * @param text words
     * @return the width their glyphs' ink takes at this scale, the font's trailing advance after the last glyph left
     *     out, rounded up to a whole pixel
     */
    int inkWidth(String text) {
        int advance = Math.max(0, font.width(text) - TRAILING_ADVANCE);
        return isFull() ? advance : (int) Math.ceil(advance * scale);
    }

    /**
     * @param button the button the label stands in
     * @param label  the label
     * @return where the label's top-left draws in the button, its x then its y
     */
    float[] labelOrigin(ButtonPainter.Box button, String label) {
        // The glyphs' ink: the run's advance less the pixel after its last glyph, and the line less its empty row and
        // its shadow's row.
        float inkWidth = Math.max(0, font.width(label) - TRAILING_ADVANCE) * scale;
        float inkHeight = Math.max(0, font.lineHeight - GLYPH_GAP - SHADOW_ROW) * scale;
        return new float[] {button.left() + (button.width() - inkWidth) / HALF,
            button.top() + (button.height() - inkHeight) / HALF};
    }

    /**
     * Draws words with their top-left at a place between whole pixels, scaled about that place, as a label centered
     * in its button draws.
     *
     * @param graphics the frame's graphics
     * @param text     the words
     * @param origin   their left edge, then their top
     * @param color    their color
     */
    void draw(GuiGraphicsExtractor graphics, String text, float[] origin, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(origin[0], origin[1]);
        pose.scale(scale);
        graphics.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    /**
     * @param font the font
     * @return how many rows a glyph's ink stands at the font's own size: its line less the empty row under its
     *     glyphs and its shadow's row
     */
    static int inkRows(Font font) {
        return Math.max(1, font.lineHeight - GLYPH_GAP - SHADOW_ROW);
    }

    /**
     * @return the height a line of text takes at this scale, rounded up to a whole pixel
     */
    int lineHeight() {
        return isFull() ? font.lineHeight : (int) Math.ceil(font.lineHeight * scale);
    }

    /**
     * @param top    a band's top
     * @param height the band's height
     * @return the text top that centers this lettering's glyphs in the band
     */
    int centeredTop(int top, int height) {
        return top + (height - lineHeight() + GLYPH_GAP) / HALF;
    }

    /**
     * @param text  words
     * @param width the room they may take at this scale
     * @return the longest start of the words that fits the room
     */
    String fitting(String text, int width) {
        return font.plainSubstrByWidth(text, isFull() ? width : (int) Math.floor(width / scale));
    }

    /**
     * @param text styled words
     * @return the width they take at this scale, their style's glyphs measured, rounded up to a whole pixel
     */
    int width(Component text) {
        return isFull() ? font.width(text) : (int) Math.ceil(font.width(text) * scale);
    }

    /**
     * Draws styled words with their top-left at the place given, scaled about that place.
     *
     * @param graphics the frame's graphics
     * @param text     the styled words
     * @param x        their left edge
     * @param y        their top
     * @param color    their color
     */
    void draw(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        if (isFull()) {
            graphics.text(font, text, x, y, color);
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        graphics.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    /**
     * Draws words with their top-left at the place given, scaled about that place.
     *
     * @param graphics the frame's graphics
     * @param text     the words
     * @param x        their left edge
     * @param y        their top
     * @param color    their color
     */
    void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        if (isFull()) {
            graphics.text(font, text, x, y, color);
            return;
        }
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale);
        graphics.text(font, text, 0, 0, color);
        pose.popMatrix();
    }
}

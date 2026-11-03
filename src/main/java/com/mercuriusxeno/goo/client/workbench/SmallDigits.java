package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws a goo amount below the font's one size, through a pose scale, as every amount beside a type icon draws in
 * tooltips, the items value preview and the recipe counts: a pixel lower than centred on its icon, a pixel nearer it
 * and a pixel further from the next (decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere). A part under 1
 * still stacks over its bar through StackedFractions, inside the smaller scale.
 */
final class SmallDigits {
    /** The scale the digits draw at, the font's 8px glyphs reading 6px. */
    static final float SCALE = 0.75f;
    /** A small glyph's height on the screen. */
    static final int GLYPH_HEIGHT = 6;

    private SmallDigits() {
    }

    /**
     * The type icon an amount stands beside.
     *
     * @param right the icon's right edge
     * @param top   its top edge
     * @param size  its side
     * @param gap   the room a full-size amount would leave after it
     */
    record Icon(int right, int top, int size, int gap) {
    }

    /**
     * @param font   the font
     * @param amount an amount as the server writes it
     * @return the width it draws at, small
     */
    static int width(Font font, String amount) {
        return (int) Math.ceil(StackedFractions.width(font, StackedFractions.of(amount)) * SCALE);
    }

    /**
     * @param font   the font
     * @param amount an amount as the server writes it
     * @return the room it takes before the next piece: its width and the pixel further it stands from the next icon
     */
    static int advance(Font font, String amount) {
        return width(font, amount) + 1;
    }

    /**
     * @param graphics the frame's graphics
     * @param font     the font
     * @param amount   the amount as the server writes it
     * @param icon     the type icon it stands beside
     * @param color    its color
     */
    static void drawBesideIcon(GuiGraphicsExtractor graphics, Font font, String amount, Icon icon, int color) {
        draw(graphics, font, amount, icon.right() + icon.gap() - 1,
                icon.top() + GooTypeIcons.centered(icon.size(), GLYPH_HEIGHT) + 1, color);
    }

    /**
     * @param graphics the frame's graphics
     * @param font     the font
     * @param amount   the amount as the server writes it
     * @param x        its left edge on the screen
     * @param top      its glyphs' top on the screen
     * @param color    its color
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, String amount, int x, int top, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, top);
        graphics.pose().scale(SCALE, SCALE);
        StackedFractions.draw(graphics, font, amount, 0, 0, color);
        graphics.pose().popMatrix();
    }
}

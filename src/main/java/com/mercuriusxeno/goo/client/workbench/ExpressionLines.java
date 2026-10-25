package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Lays an expression's runs across lines of a width, each $name link drawn in the link color and handed to the
 * caller where it lands, so a click on it opens its variable (decision
 * references-link-to-their-variable-and-hover-a-card).
 */
final class ExpressionLines {
    private static final int LINE_HEIGHT = 10;

    private ExpressionLines() {
    }

    /**
     * Where one piece of a link landed.
     */
    @FunctionalInterface
    interface LinkSink {
        /**
         * @param variable the variable the link opens
         * @param x        the piece's left edge
         * @param y        the piece's top edge
         * @param width    the piece's width
         */
        void landed(String variable, int x, int y, int width);
    }

    /**
     * @param font  the font
     * @param nodes the expression's runs
     * @param width the width the lines fill
     * @return how many lines the runs take, at least one
     */
    static int measure(Font font, List<ExpressionReferences.Node> nodes, int width) {
        return lay(font, nodes, 0, 0, width, null, 0, 0, null);
    }

    /**
     * @param graphics  the frame's graphics
     * @param font      the font
     * @param nodes     the expression's runs
     * @param x         the lines' left edge
     * @param y         the first line's top edge
     * @param width     the width the lines fill
     * @param textColor plain text's color
     * @param linkColor a link's color
     * @param links     takes where each piece of a link landed
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, List<ExpressionReferences.Node> nodes, int x, int y,
                     int width, int textColor, int linkColor, LinkSink links) {
        lay(font, nodes, x, y, width, graphics, textColor, linkColor, links);
    }

    private static int lay(Font font, List<ExpressionReferences.Node> nodes, int x, int y, int width,
                           @Nullable GuiGraphicsExtractor graphics, int textColor, int linkColor,
                           @Nullable LinkSink links) {
        int lines = 1;
        int cursor = x;
        for (ExpressionReferences.Node node : nodes) {
            String rest = node.label();
            while (!rest.isEmpty()) {
                String piece = font.plainSubstrByWidth(rest, x + width - cursor);
                if (piece.isEmpty() && cursor > x) {
                    cursor = x;
                    lines++;
                    continue;
                }
                if (piece.isEmpty()) {
                    piece = rest.substring(0, 1);
                }
                int top = y + (lines - 1) * LINE_HEIGHT;
                int pieceWidth = font.width(piece);
                if (graphics != null && links != null) {
                    graphics.text(font, piece, cursor, top, node.link() ? linkColor : textColor);
                    if (node.variable() != null) {
                        links.landed(node.variable(), cursor, top, pieceWidth);
                    }
                }
                cursor += pieceWidth;
                rest = rest.substring(piece.length());
                if (!rest.isEmpty()) {
                    cursor = x;
                    lines++;
                }
            }
        }
        return lines;
    }
}

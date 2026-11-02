package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Draws every clickable option the manager offers as a bordered box with its label inside, the box its click target,
 * so an option reads as a button and not as colored text
 * (decision blue-text-options-draw-as-bordered-buttons).
 */
final class ButtonPainter {
    /** The one link color: a button's label and border, and a prefab named inside an expression. */
    static final int LINK = 0xFF80C0FF;
    /** The gold a restore arrow draws in (decision every-delete-marks-for-removal-and-a-gold-arrow-restores). */
    static final int GOLD = 0xFFE0C060;
    /** The circular arrow a restore button reads. */
    static final String RESTORE = "↺";
    private static final String RESTORE_HINT = "Restore";
    private static final int MARKED_COLOR = 0xFFA0A0A0;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    // A strike through a marked name stands at the glyphs' middle.
    private static final int STRIKE_DROP = 4;
    /** A button's height. */
    static final int HEIGHT = 14;
    /** The room between the border and the label on either side. */
    static final int PADDING = 3;
    /** A button's border color. */
    static final int BORDER = 0xFF5070A0;
    /** A button's inner fill. */
    static final int FILL = 0xFF182030;
    /** How far a button drawn at a text's top reaches above that top. */
    static final int TEXT_NUDGE = 3;
    // A glyph's baseline stands seven pixels under the text's top, the underline one under that, as the font's own.
    private static final int UNDERLINE_DROP = 8;

    private ButtonPainter() {
    }

    /**
     * A button's box, which is also its click target.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param width  its width
     * @param height its height
     */
    record Box(int left, int top, int width, int height) {
        /**
         * @return the x past the box
         */
        int right() {
            return left + width;
        }
    }

    /**
     * @param labelWidth the label's width
     * @param x          the button's left edge
     * @param lineTop    the top of the line it stands centered on
     * @param lineHeight that line's height
     * @return the box the button takes
     */
    static Box layout(int labelWidth, int x, int lineTop, int lineHeight) {
        int above = Math.max(0, lineHeight - HEIGHT) >> 1;
        return new Box(x, lineTop + above, labelWidth + PADDING + PADDING, HEIGHT);
    }

    /**
     * Reserves the box as the button's click target.
     *
     * @param targets  the frame's click areas
     * @param box      the button's box
     * @param pressed  what a click does
     * @param hoverKey what hovering names, null for nothing
     */
    static void reserve(ClickTargets targets, Box box, Runnable pressed, @Nullable String hoverKey) {
        targets.add(box.left(), box.top(), box.width(), box.height(), pressed, hoverKey);
    }

    /**
     * Draws a button on a line and takes its click.
     *
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param label      the button's label
     * @param x          its left edge
     * @param lineTop    the top of the line it stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param pressed    what a click does
     * @param hoverKey   what hovering names, null for nothing
     * @return the box it took
     */
    static Box draw(GuiGraphicsExtractor graphics, Font font, String label, int x, int lineTop, int lineHeight,
                    ClickTargets targets, Runnable pressed, @Nullable String hoverKey) {
        return drawColored(graphics, font, label, LINK, x, lineTop, lineHeight, targets, pressed, hoverKey);
    }

    /**
     * Draws a button whose label takes a color of its own, as a destructive Delete reads red, and takes its click.
     *
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param label      the button's label
     * @param color      the label's color
     * @param x          its left edge
     * @param lineTop    the top of the line it stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param pressed    what a click does
     * @param hoverKey   what hovering names, null for nothing
     * @return the box it took
     */
    static Box drawColored(GuiGraphicsExtractor graphics, Font font, String label, int color, int x, int lineTop,
                           int lineHeight, ClickTargets targets, Runnable pressed, @Nullable String hoverKey) {
        Box box = layout(font.width(label), x, lineTop, lineHeight);
        drawFrame(graphics, box);
        graphics.text(font, label, box.left() + PADDING, box.top() + TEXT_NUDGE, color);
        reserve(targets, box, pressed, hoverKey);
        return box;
    }

    /**
     * Draws a link button on a line whose shift + left click follows it, a plain left click passing to the area under
     * it; its label underlines while shift is held.
     * Decision shift-left-click-follows-plain-left-modifies.
     * Decision holding-shift-underlines-every-link.
     *
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param label      the button's label
     * @param x          its left edge
     * @param lineTop    the top of the line it stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param follow     what a shift + left click does
     * @param hoverKey   what hovering names, null for nothing
     * @return the box it took
     */
    static Box drawFollow(GuiGraphicsExtractor graphics, Font font, String label, int x, int lineTop, int lineHeight,
                          ClickTargets targets, Runnable follow, @Nullable String hoverKey) {
        Box box = layout(font.width(label), x, lineTop, lineHeight);
        drawFrame(graphics, box);
        graphics.text(font, label, box.left() + PADDING, box.top() + TEXT_NUDGE, LINK);
        underlineWhileShiftHeld(graphics, targets, box.left() + PADDING, box.top() + TEXT_NUDGE, font.width(label),
                LINK);
        targets.addFollow(box.left(), box.top(), box.width(), box.height(), follow, hoverKey);
        return box;
    }

    /**
     * Draws a button naming a ref whose plain left click modifies it and whose shift + left click follows it; its
     * label underlines while shift is held.
     * Decision shift-left-click-follows-plain-left-modifies.
     * Decision holding-shift-underlines-every-link.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the button's label
     * @param box      the button's box
     * @param targets  the frame's click areas
     * @param gestures what a plain and a shift + left click do
     * @param hoverKey what hovering names, null for nothing
     */
    static void drawLink(GuiGraphicsExtractor graphics, Font font, String label, Box box, ClickTargets targets,
                         ClickTargets.LinkGestures gestures, @Nullable String hoverKey) {
        drawFrame(graphics, box);
        graphics.text(font, label, box.left() + PADDING, box.top() + TEXT_NUDGE, LINK);
        underlineWhileShiftHeld(graphics, targets, box.left() + PADDING, box.top() + TEXT_NUDGE, font.width(label),
                LINK);
        targets.addLink(box.left(), box.top(), box.width(), box.height(), gestures, hoverKey);
    }

    /**
     * Draws a one-pixel line under a link's text while the frame holds shift.
     * Decision holding-shift-underlines-every-link.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas, which say whether shift is held
     * @param x        the text's left edge
     * @param textTop  the text's top
     * @param width    the text's width
     * @param color    the link's color
     */
    static void underlineWhileShiftHeld(GuiGraphicsExtractor graphics, ClickTargets targets, int x, int textTop,
                                        int width, int color) {
        if (targets.shiftHeld()) {
            graphics.fill(x, textTop + UNDERLINE_DROP, x + width, textTop + UNDERLINE_DROP + 1, color);
        }
    }

    /**
     * Draws a button's border and inner fill, for content a caller draws inside it, as the builder's clickable runs.
     *
     * @param graphics the frame's graphics
     * @param box      the button's box
     */
    static void drawFrame(GuiGraphicsExtractor graphics, Box box) {
        int bottom = box.top() + box.height();
        graphics.fill(box.left(), box.top(), box.right(), bottom, BORDER);
        graphics.fill(box.left() + 1, box.top() + 1, box.right() - 1, bottom - 1, FILL);
    }

    /**
     * Draws a button whose label stands where a line of text would, the box growing around it, and takes its click.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the button's label
     * @param x        its left edge
     * @param textTop  where the label's text stands
     * @param targets  the frame's click areas
     * @param pressed  what a click does
     * @return the box it took
     */
    static Box drawAtText(GuiGraphicsExtractor graphics, Font font, String label, int x, int textTop,
                          ClickTargets targets, Runnable pressed) {
        return draw(graphics, font, label, x, textTop - TEXT_NUDGE, HEIGHT, targets, pressed, null);
    }

    /**
     * @param font  the font
     * @param label a button's label
     * @return the width the button takes
     */
    static int width(Font font, String label) {
        return font.width(label) + PADDING + PADDING;
    }

    /**
     * Draws the gold circular arrow standing in a marked entry's ✗, right-aligned to where the ✗ stood.
     * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
     *
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param right      the right edge the ✗ stood against
     * @param lineTop    the top of the line it stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param restored   what a click does
     * @return the box it took
     */
    static Box drawRestore(GuiGraphicsExtractor graphics, Font font, int right, int lineTop, int lineHeight,
                           ClickTargets targets, Runnable restored) {
        return drawColored(graphics, font, RESTORE, GOLD, right - width(font, RESTORE), lineTop, lineHeight, targets,
                restored, BuilderOverlays.hintKey(RESTORE_HINT));
    }

    /**
     * Draws an entry's name in white, or dimmed and struck through where it is marked for removal, as it reads
     * until the save.
     * Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param name     the shown name
     * @param x        its left edge
     * @param textTop  its text's top
     * @param marked   whether the entry is marked for removal
     */
    static void drawEntryName(GuiGraphicsExtractor graphics, Font font, String name, int x, int textTop,
                              boolean marked) {
        if (!marked) {
            graphics.text(font, name, x, textTop, NAME_COLOR);
            return;
        }
        graphics.text(font, name, x, textTop, MARKED_COLOR);
        graphics.fill(x, textTop + STRIKE_DROP, x + font.width(name), textTop + STRIKE_DROP + 1, MARKED_COLOR);
    }

    /**
     * Draws a button on a line and takes its click, naming nothing on hover.
     *
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param label      the button's label
     * @param x          its left edge
     * @param lineTop    the top of the line it stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param pressed    what a click does
     * @return the box it took
     */
    static Box draw(GuiGraphicsExtractor graphics, Font font, String label, int x, int lineTop, int lineHeight,
                    ClickTargets targets, Runnable pressed) {
        return draw(graphics, font, label, x, lineTop, lineHeight, targets, pressed, null);
    }
}

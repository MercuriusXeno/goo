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
    /** The circular arrow a restore button reads. */
    static final String RESTORE = "↺";
    private static final String RESTORE_HINT = "Restore";
    // A strike through a marked name stands at the glyphs' middle.
    private static final int STRIKE_DROP = 4;
    /** A button's height. */
    static final int HEIGHT = 14;
    /** The room between the border and the label on either side. */
    static final int PADDING = 3;
    /**
     * The room between a name and its rename pencil on every pane (decision
     * one-search-row-per-list-with-its-create-control-beside-it).
     */
    static final int PENCIL_GAP = RowLayout.BUTTON_GAP;
    /** The room a type-icon button leaves each side of its icon (decision type-icon-buttons-shrink-their-padding). */
    static final int ICON_PADDING = 1;
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
        return drawColored(graphics, font, label, Palette.LINK, x, lineTop, lineHeight, targets, pressed, hoverKey);
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
        return drawColored(graphics, Lettering.full(font), label, color, new Box(x, lineTop, 0, lineHeight), targets,
                pressed, hoverKey);
    }

    /**
     * Draws a selected button, as the manager's active tab stands: the selected fill inside the button's border, its
     * label in the text color, and takes its click (decision one-flat-painter-draws-every-button).
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the button's label
     * @param x        its left edge
     * @param top      its top
     * @param targets  the frame's click areas
     * @param pressed  what a click does
     * @return the box it took
     */
    static Box drawSelected(GuiGraphicsExtractor graphics, Font font, String label, int x, int top,
                            ClickTargets targets, Runnable pressed) {
        Lettering lettering = Lettering.full(font);
        Box box = layout(lettering.width(label), x, top, HEIGHT);
        Frame.draw(graphics, Frame.SELECTED, box);
        drawLabel(graphics, lettering, label, box, Palette.TEXT);
        reserve(targets, box, pressed, null);
        return box;
    }

    /**
     * Draws a square button a button's height on each side, its label's ink centered in it, as the manager's X and
     * [?] stand, and takes its click.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the button's label
     * @param x        its left edge
     * @param top      its top
     * @param targets  the frame's click areas
     * @param pressed  what a click does
     * @return the box it took
     */
    static Box drawSquare(GuiGraphicsExtractor graphics, Font font, String label, int x, int top,
                          ClickTargets targets, Runnable pressed) {
        Lettering lettering = Lettering.full(font);
        Box box = new Box(x, top, HEIGHT, HEIGHT);
        Frame.draw(graphics, Frame.BUTTON, box);
        lettering.draw(graphics, label, lettering.labelOrigin(box, label), Palette.LINK);
        reserve(targets, box, pressed, null);
        return box;
    }

    /**
     * Draws a button lettered at a scale, its label's color its own, and takes its click.
     *
     * @param graphics  the frame's graphics
     * @param lettering the label's font and scale
     * @param label     the button's label
     * @param color     the label's color
     * @param line      the button's left edge as left, and its line's top and height; the width is unread
     * @param targets   the frame's click areas
     * @param pressed   what a click does
     * @param hoverKey  what hovering names, null for nothing
     * @return the box it took
     */
    static Box drawColored(GuiGraphicsExtractor graphics, Lettering lettering, String label, int color, Box line,
                           ClickTargets targets, Runnable pressed, @Nullable String hoverKey) {
        Box box = layout(lettering.width(label), line.left(), line.top(), line.height());
        Frame.draw(graphics, Frame.BUTTON, box);
        drawLabel(graphics, lettering, label, box, color);
        reserve(targets, box, pressed, hoverKey);
        return box;
    }

    // A full-size label stands its padding in and centers on the box's height; a scaled one centers its glyphs'
    // ink in the box, between whole pixels where the halves fall there, so an operator's + or - stands in the middle
    // of its button (the operator saw them too far left and slightly too high). Answers where the label stood, its
    // left edge then its top, rounded to whole pixels.
    static int[] drawLabel(GuiGraphicsExtractor graphics, Lettering lettering, String label, Box box, int color) {
        if (lettering.isFull()) {
            int top = lettering.centeredTop(box.top(), box.height());
            lettering.draw(graphics, label, box.left() + PADDING, top, color);
            return new int[] {box.left() + PADDING, top};
        }
        float[] origin = lettering.labelOrigin(box, label);
        lettering.draw(graphics, label, origin, color);
        return new int[] {Math.round(origin[0]), Math.round(origin[1])};
    }

    // The underline stands under a scaled label's glyphs as it does under the font's own.
    private static int underlineDrop(Lettering lettering) {
        return lettering.isFull() ? UNDERLINE_DROP : Math.round(UNDERLINE_DROP * lettering.scale());
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
        return drawFollow(graphics, Lettering.full(font), label, new Box(x, lineTop, 0, lineHeight), targets, follow,
                hoverKey);
    }

    /**
     * Draws a link button lettered at a scale whose shift + left click follows it; its label underlines while shift
     * is held.
     *
     * @param graphics  the frame's graphics
     * @param lettering the label's font and scale
     * @param label     the button's label
     * @param line      the button's left edge as left, and its line's top and height; the width is unread
     * @param targets   the frame's click areas
     * @param follow    what a shift + left click does
     * @param hoverKey  what hovering names, null for nothing
     * @return the box it took
     */
    static Box drawFollow(GuiGraphicsExtractor graphics, Lettering lettering, String label, Box line,
                          ClickTargets targets, Runnable follow, @Nullable String hoverKey) {
        Box box = layout(lettering.width(label), line.left(), line.top(), line.height());
        Frame.draw(graphics, Frame.BUTTON, box);
        drawLinkLabel(graphics, lettering, label, box, targets);
        targets.addFollow(box.left(), box.top(), box.width(), box.height(), follow, hoverKey);
        return box;
    }

    private static void drawLinkLabel(GuiGraphicsExtractor graphics, Lettering lettering, String label, Box box,
                                      ClickTargets targets) {
        int[] place = drawLabel(graphics, lettering, label, box, Palette.LINK);
        underlineWhileShiftHeld(graphics, targets, lettering, place[0], place[1], lettering.width(label), Palette.LINK);
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
        drawLink(graphics, Lettering.full(font), label, box, targets, gestures, hoverKey);
    }

    /**
     * Draws a button lettered at a scale naming a ref whose plain left click modifies it and whose shift + left click
     * follows it; its label underlines while shift is held.
     *
     * @param graphics  the frame's graphics
     * @param lettering the label's font and scale
     * @param label     the button's label
     * @param box       the button's box
     * @param targets   the frame's click areas
     * @param gestures  what a plain and a shift + left click do
     * @param hoverKey  what hovering names, null for nothing
     */
    static void drawLink(GuiGraphicsExtractor graphics, Lettering lettering, String label, Box box,
                         ClickTargets targets, ClickTargets.LinkGestures gestures, @Nullable String hoverKey) {
        Frame.draw(graphics, Frame.BUTTON, box);
        drawLinkLabel(graphics, lettering, label, box, targets);
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
     * Draws a one-pixel line under a link's text lettered at a scale while the frame holds shift, under its glyphs
     * as under the font's own.
     *
     * @param graphics  the frame's graphics
     * @param targets   the frame's click areas, which say whether shift is held
     * @param lettering the text's font and scale
     * @param x         the text's left edge
     * @param textTop   the text's top
     * @param width     the text's width
     * @param color     the link's color
     */
    static void underlineWhileShiftHeld(GuiGraphicsExtractor graphics, ClickTargets targets, Lettering lettering,
                                        int x, int textTop, int width, int color) {
        if (targets.shiftHeld()) {
            int drop = underlineDrop(lettering);
            graphics.fill(x, textTop + drop, x + width, textTop + drop + 1, color);
        }
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
        return draw(graphics, font, label, x, textTop - labelInset(font), HEIGHT, targets, pressed, null);
    }

    /**
     * @param font the font
     * @return how far under a button's top its full-size label's text stands, the label centered on the button
     *     (decision one-lettering-primitive-names-three-text-roles)
     */
    static int labelInset(Font font) {
        return Lettering.full(font).centeredTop(0, HEIGHT);
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
     * @param lettering the label's font and scale
     * @param label     a button's label
     * @return the width the button takes
     */
    static int width(Lettering lettering, String label) {
        return lettering.width(label) + PADDING + PADDING;
    }

    /**
     * @param iconSize a type icon's size
     * @return the width a type-icon button takes: the icon and one pixel each side
     */
    static int iconWidth(int iconSize) {
        // Decision type-icon-buttons-shrink-their-padding.
        return iconSize + ICON_PADDING + ICON_PADDING;
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
        return drawColored(graphics, font, RESTORE, Palette.GOLD, right - width(font, RESTORE), lineTop, lineHeight,
                targets, restored, BuilderOverlays.hintKey(RESTORE_HINT));
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
        drawEntryName(graphics, font, name, x, textTop, marked, Palette.TEXT);
    }

    /**
     * Draws an entry's name in a color of its own, as a group naming an unknown member reads red, struck through and
     * dimmed while the entry is marked for removal.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param name     the shown name
     * @param x        its left edge
     * @param textTop  its text's top
     * @param marked   whether the entry is marked for removal
     * @param color    the name's color while it stands unmarked
     */
    static void drawEntryName(GuiGraphicsExtractor graphics, Font font, String name, int x, int textTop,
                              boolean marked, int color) {
        if (!marked) {
            graphics.text(font, name, x, textTop, color);
            return;
        }
        graphics.text(font, name, x, textTop, Palette.MUTED);
        graphics.fill(x, textTop + STRIKE_DROP, x + font.width(name), textTop + STRIKE_DROP + 1, Palette.MUTED);
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

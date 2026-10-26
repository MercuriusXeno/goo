package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lays a builder's words and controls left to right across an area, a piece too wide for what is left of its line
 * starting the next, indented under the first, so no control runs off the pane; each control names what it does on
 * hover (decision expression-builder-composes-every-form-without-typing). The expression builder and the Mutators
 * rows draw through it.
 */
final class ControlFlow {
    /** A line's height, an icon's. */
    static final int LINE = BuilderOverlays.LINE;
    private static final int GAP = BuilderOverlays.GAP;
    private static final int TEXT_NUDGE = 5;
    private static final int ICON_NUDGE = 1;
    private static final int INDENT = 12;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LINK = ButtonPainter.LINK;

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final ClickTargets targets;
    private final @Nullable ItemIcons icons;
    private int left;
    private final int right;
    private int lineLeft;
    private int x;
    private int y;
    private int pieceLeft;
    private int pieceTop;
    private int pieceWidth;

    /**
     * @param graphics the frame's graphics
     * @param font     the font
     * @param targets  the frame's click areas
     * @param icons    the items' icons, null where no item draws
     * @param left     the area's left edge
     * @param right    the area's right edge
     * @param top      the first line's top
     */
    ControlFlow(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, @Nullable ItemIcons icons, int left,
                int right, int top) {
        this.graphics = graphics;
        this.font = font;
        this.targets = targets;
        this.icons = icons;
        this.left = left;
        this.right = right;
        this.lineLeft = left;
        this.x = left;
        this.y = top;
    }

    /**
     * @return the y below the last line
     */
    int bottom() {
        return y + LINE;
    }

    /**
     * @return where the next piece stands, for a list opened from it
     */
    BuilderOverlays.Anchor anchor() {
        return new BuilderOverlays.Anchor(x, y);
    }

    /**
     * Starts a fresh line where the current one holds anything.
     */
    void newLine() {
        if (x > lineLeft || lineLeft > left) {
            y += LINE;
        }
        lineLeft = left;
        x = left;
    }

    private void reserve(int width) {
        if (x + width > right && x > lineLeft) {
            y += LINE;
            lineLeft = left + INDENT;
            x = lineLeft;
        }
    }

    /**
     * @param text  words that take no click
     * @param color their color
     */
    void text(String text, int color) {
        reserve(font.width(text));
        graphics.text(font, text, x, y + TEXT_NUDGE, color);
        markPiece(font.width(text));
        x += font.width(text) + GAP;
    }

    private void markPiece(int width) {
        pieceLeft = x;
        pieceTop = y;
        pieceWidth = width;
    }

    /**
     * Lays a hover area over the piece drawn last, so it names what the hover key shows while a click passes to the
     * area under it.
     *
     * @param hoverKey what hovering the piece names
     */
    void hoverLast(String hoverKey) {
        targets.addHover(pieceLeft, pieceTop, pieceWidth, LINE, hoverKey);
    }

    /**
     * A line of its own, cut to the area's width.
     *
     * @param text  the note
     * @param color its color
     */
    void note(String text, int color) {
        newLine();
        String shown = font.plainSubstrByWidth(text, right - left);
        graphics.text(font, shown, x, y + TEXT_NUDGE, color);
        x += font.width(shown) + GAP;
    }

    /**
     * @param label   the control's label
     * @param hint    what it does, shown on hover
     * @param pressed takes where it was clicked
     */
    void control(String label, String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(ButtonPainter.width(font, label));
        markPiece(ButtonPainter.width(font, label));
        x = BuilderOverlays.control(graphics, font, label, hint, x, y, targets, pressed);
    }

    /**
     * One option of a choice: the chosen one plain, the others links that choose them.
     *
     * @param label  the option's label
     * @param hint   what choosing it does
     * @param chosen whether it is the one chosen
     * @param choose chooses it
     */
    void choice(String label, String hint, boolean chosen, Runnable choose) {
        choiceAt(label, hint, chosen, at -> choose.run());
    }

    /**
     * One option of a choice whose choosing opens a list below it.
     *
     * @param label  the option's label
     * @param hint   what choosing it does
     * @param chosen whether it is the one chosen
     * @param choose takes where it was clicked
     */
    void choiceAt(String label, String hint, boolean chosen, Consumer<BuilderOverlays.Anchor> choose) {
        if (chosen) {
            text(label, WHITE);
        } else {
            control(label, hint, choose);
        }
    }

    /**
     * Starts a fresh line indented one step further, for the terms a sum holds.
     */
    void indent() {
        newLine();
        left += INDENT;
        lineLeft = left;
        x = left;
    }

    /**
     * Starts a fresh line one step less indented.
     */
    void outdent() {
        newLine();
        left -= INDENT;
        lineLeft = left;
        x = left;
    }

    /**
     * A goo type's icon, named on hover with the note that a click changes it.
     *
     * @param type    the goo type's id
     * @param pressed takes where it was clicked
     */
    void typeIcon(String type, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(GooTypeIcons.SIZE);
        int iconX = x;
        int iconY = y;
        GooTypeIcons.blit(graphics, type, iconX, iconY + ICON_NUDGE);
        targets.add(iconX, iconY, GooTypeIcons.SIZE, LINE,
                () -> pressed.accept(new BuilderOverlays.Anchor(iconX, iconY)), GooTypeIcons.pickHoverKey(type));
        x += GooTypeIcons.SIZE + GAP;
    }

    /**
     * Curated runs on one line, a click on them running the action where one is given.
     *
     * @param runs    the runs
     * @param hint    what a click does, or why none does, null for nothing
     * @param pressed takes where they were clicked, null for no click
     */
    void runs(List<ExpressionLines.Run> runs, @Nullable String hint, @Nullable Consumer<BuilderOverlays.Anchor> pressed) {
        if (runs.isEmpty()) {
            return;
        }
        int width = ExpressionLines.width(font, runs);
        reserve(width);
        addRunsTarget(width, hint, pressed);
        int runsTop = ExpressionLines.lineHeight(runs) == LINE ? y : y + TEXT_NUDGE;
        ExpressionLines.draw(font, runs, x, runsTop, width + 1, new ExpressionLines.Painter(graphics, icons,
                targets, pressed == null ? WHITE : LINK, LINK, (name, linkX, linkY, linkWidth) -> { }));
        x += width + GAP;
    }

    // The runs' click, or their hint alone where they take none.
    private void addRunsTarget(int width, @Nullable String hint, @Nullable Consumer<BuilderOverlays.Anchor> pressed) {
        int runsX = x;
        int runsY = y;
        String hoverKey = hint == null ? null : BuilderOverlays.hintKey(hint);
        if (pressed != null) {
            targets.add(runsX, runsY, width, LINE, () -> pressed.accept(new BuilderOverlays.Anchor(runsX, runsY)),
                    hoverKey);
        } else if (hoverKey != null) {
            targets.addHover(runsX, runsY, width, LINE, hoverKey);
        }
    }
}

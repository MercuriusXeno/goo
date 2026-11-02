package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
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
    private static final int HALF = 2;
    private static final int RAIL_WIDTH = 4;
    private static final int RAIL_INSET = 3;
    private static final int RAIL_COLOR = 0xFF7080A0;
    private static final int OUTLINE_COLOR = 0x60FFFFFF;
    /** How far one indent step moves a line's left edge. */
    static final int INDENT = 12;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String DROPDOWN_MARKER = " ▼";
    private static final String NONE_CHOSEN = "pick one";

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    private final ClickTargets targets;
    private final @Nullable ItemIcons icons;
    private final Consumer<String> followRef;
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
        this(graphics, font, targets, icons, new Span(left, right, top), ref -> { });
    }

    /**
     * Where a flow's lines lay.
     *
     * @param left  the area's left edge
     * @param right the area's right edge
     * @param top   the first line's top
     */
    record Span(int left, int right, int top) {
    }

    /**
     * @param graphics  the frame's graphics
     * @param font      the font
     * @param targets   the frame's click areas
     * @param icons     the items' icons, null where no item draws
     * @param span      where the lines lay
     * @param followRef opens the prefab a reference run names, on a shift + left click
     */
    ControlFlow(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, @Nullable ItemIcons icons, Span span,
                Consumer<String> followRef) {
        this.graphics = graphics;
        this.font = font;
        this.targets = targets;
        this.icons = icons;
        this.followRef = followRef;
        this.left = span.left();
        this.right = span.right();
        this.lineLeft = span.left();
        this.x = span.left();
        this.y = span.top();
    }

    /**
     * @param lineTop a flow line's top
     * @return where its words and its controls' labels stand, so a label drawn beside the flow shares the line
     */
    static int textTop(int lineTop) {
        return lineTop + TEXT_NUDGE;
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

    /**
     * Continues on a fresh line one indent step in, as a line that ran out wraps, where the current line holds
     * anything.
     */
    void wrapLine() {
        if (x > lineLeft) {
            y += LINE;
            lineLeft = left + INDENT;
            x = lineLeft;
        }
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
    void control(String label, @Nullable String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(ButtonPainter.width(font, label));
        markPiece(ButtonPainter.width(font, label));
        x = BuilderOverlays.control(graphics, font, label, hint, x, y, targets, pressed);
    }

    /**
     * A control naming a ref: a plain left click runs pressed, a shift + left click follows the ref, and the label
     * underlines while shift is held.
     * Decision shift-left-click-follows-plain-left-modifies.
     *
     * @param label   the control's label
     * @param hint    what a plain click does, shown on hover
     * @param pressed takes where it was clicked
     * @param follow  opens what the label names
     */
    void linkControl(String label, @Nullable String hint, Consumer<BuilderOverlays.Anchor> pressed, Runnable follow) {
        int width = ButtonPainter.width(font, label);
        reserve(width);
        markPiece(width);
        int controlX = x;
        int controlY = y;
        ButtonPainter.Box box = ButtonPainter.layout(font.width(label), controlX, controlY, LINE);
        ButtonPainter.drawLink(graphics, font, label, box, targets, new ClickTargets.LinkGestures(
                () -> pressed.accept(new BuilderOverlays.Anchor(controlX, controlY)), follow),
                BuilderOverlays.hintKey(hint));
        x = box.right() + GAP;
    }

    /**
     * A ref drawn as a link button that a shift + left click follows, a plain left click passing to the area under it.
     * Decision holding-shift-underlines-every-link.
     *
     * @param label    the ref's label
     * @param hoverKey what hovering names
     * @param follow   opens what the label names
     */
    void followLink(String label, String hoverKey, Runnable follow) {
        int width = ButtonPainter.width(font, label);
        reserve(width);
        markPiece(width);
        x = ButtonPainter.drawFollow(graphics, font, label, x, y, LINE, targets, follow, hoverKey).right() + GAP;
    }

    /**
     * A control whose label takes a color of its own, as a remove reads red and done green.
     *
     * @param label   the control's label
     * @param color   the label's color
     * @param hint    what it does, shown on hover
     * @param pressed takes where it was clicked
     */
    void coloredControl(String label, int color, @Nullable String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(ButtonPainter.width(font, label));
        markPiece(ButtonPainter.width(font, label));
        int controlX = x;
        int controlY = y;
        ButtonPainter.Box box = ButtonPainter.drawColored(graphics, font, label, color, controlX, controlY, LINE,
                targets, () -> pressed.accept(new BuilderOverlays.Anchor(controlX, controlY)),
                BuilderOverlays.hintKey(hint));
        x = box.right() + GAP;
    }

    /**
     * @param label the chosen option's label
     * @return what the set's dropdown button reads: the label and a down arrow marking that it lists more
     */
    static String dropdownLabel(String label) {
        return label + DROPDOWN_MARKER;
    }

    /**
     * An exclusive set as one dropdown button reading the chosen option, whose click lists every option under it
     * (decision exclusive-choice-sets-draw-as-dropdowns).
     *
     * @param set      the set's options and which is chosen
     * @param overlays where the list opens
     */
    void choices(ChoiceSet set, BuilderOverlays overlays) {
        choices(set.options(), set.chosen(), overlays);
    }

    /**
     * An exclusive set's options as each is offered, and which of them is chosen.
     */
    static final class ChoiceSet {
        private final List<BuilderOverlays.Option> options = new ArrayList<>();
        private int chosen = -1;

        /**
         * @param option   an option the set offers
         * @param isChosen whether it is the one chosen
         */
        void add(BuilderOverlays.Option option, boolean isChosen) {
            if (isChosen) {
                chosen = options.size();
            }
            options.add(option);
        }

        List<BuilderOverlays.Option> options() {
            return List.copyOf(options);
        }

        int chosen() {
            return chosen;
        }
    }

    /**
     * An exclusive set as one dropdown button reading the chosen option, whose click lists every option under it.
     *
     * @param options  the set's options, in order
     * @param chosen   the chosen option's index, below 0 where none is chosen
     * @param overlays where the list opens
     */
    void choices(List<BuilderOverlays.Option> options, int chosen, BuilderOverlays overlays) {
        boolean picked = chosen >= 0 && chosen < options.size();
        String label = picked ? options.get(chosen).label() : NONE_CHOSEN;
        String hint = picked ? options.get(chosen).hint() : null;
        control(dropdownLabel(label), hint, at -> overlays.openOptions(at, options));
    }

    /**
     * Starts a fresh line indented one step further, for the terms a sum holds.
     */
    void indent() {
        indent(INDENT);
    }

    /**
     * Starts a fresh line indented further.
     *
     * @param step how far past the current left edge the new lines start
     */
    void indent(int step) {
        newLine();
        left += step;
        lineLeft = left;
        x = left;
    }

    /**
     * Moves the left edge the next lines start at back toward the outer edge, leaving the current line standing, so
     * a control may still join it.
     *
     * @param step how far back the next lines start
     */
    void outdent(int step) {
        shift(-step);
    }

    /**
     * Moves the left edge the next lines start at, leaving the current line standing.
     *
     * @param step how far right the next lines start, negative for left
     */
    void shift(int step) {
        left += step;
    }

    /**
     * Stands the next pieces at the right end of the line, starting a fresh line where the current one holds no
     * room for them.
     *
     * @param width the pieces' width, gaps between them included
     */
    void alignRight(int width) {
        if (x + width > right && x > lineLeft) {
            y += LINE;
            lineLeft = left;
        }
        x = right - width;
    }

    /**
     * @return the font the flow measures with
     */
    Font font() {
        return font;
    }

    /**
     * @return how far past the line's left edge the next piece starts
     */
    int column() {
        return x - left;
    }

    /**
     * A goo type's icon, named on hover with the note that a click changes it.
     *
     * @param type    the goo type's id
     * @param pressed takes where it was clicked
     */
    void typeIcon(String type, Consumer<BuilderOverlays.Anchor> pressed) {
        // Decision goo-type-icons-draw-native-everywhere.
        // Decision goo-types-in-builders-draw-as-buttons: the icon stands inside a button's border, the line's
        // height, so it reads as clickable.
        int size = GooTypeIcons.sizeOf(type);
        int width = size + ButtonPainter.PADDING + ButtonPainter.PADDING;
        reserve(width);
        markPiece(width);
        int iconX = x;
        int iconY = y;
        ButtonPainter.drawFrame(graphics, new ButtonPainter.Box(iconX, iconY, width, LINE));
        GooTypeIcons.blit(graphics, type, iconX + ButtonPainter.PADDING, iconY + GooTypeIcons.centered(LINE, size));
        targets.add(iconX, iconY, width, LINE,
                () -> pressed.accept(new BuilderOverlays.Anchor(iconX, iconY)), GooTypeIcons.pickHoverKey(type));
        x += width + GAP;
    }

    /**
     * A term's drag rail: a narrow grip a line tall, a press on it grabbing the pointer until the release (decision
     * terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     *
     * @param hoverKey what hovering names, which no tooltip reads
     * @param grab     what the press, each drag and the release move
     */
    void rail(String hoverKey, ClickTargets.PointerGrab grab) {
        reserve(RAIL_WIDTH);
        markPiece(RAIL_WIDTH);
        graphics.fill(x, y + RAIL_INSET, x + RAIL_WIDTH, y + LINE - RAIL_INSET, RAIL_COLOR);
        targets.addGrab(x, y, RAIL_WIDTH, LINE, grab);
        targets.addHover(x, y, RAIL_WIDTH, LINE, hoverKey);
        x += RAIL_WIDTH + GAP;
    }

    /**
     * Outlines the pieces drawn since the place given, where they stand on its line, as a term and its rail read as
     * one unit (decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     *
     * @param start where the first piece stood
     */
    void outlineFrom(BuilderOverlays.Anchor start) {
        if (start.y() != y) {
            return;
        }
        int left = start.x() - 1;
        int right = x - GAP + 1;
        graphics.fill(left, y, right, y + 1, OUTLINE_COLOR);
        graphics.fill(left, y + LINE - 1, right, y + LINE, OUTLINE_COLOR);
        graphics.fill(left, y, left + 1, y + LINE, OUTLINE_COLOR);
        graphics.fill(right - 1, y, right, y + LINE, OUTLINE_COLOR);
    }

    /**
     * A square button a line's height on each side, its label centered in it, as a goo type button stands that
     * tall (decision add-type-is-a-big-square-plus-question-button-below-the-list).
     *
     * @param label   its label
     * @param pressed takes where it was clicked
     */
    void squareControl(String label, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(LINE);
        markPiece(LINE);
        int boxX = x;
        int boxY = y;
        ButtonPainter.drawFrame(graphics, new ButtonPainter.Box(boxX, boxY, LINE, LINE));
        graphics.text(font, label, boxX + (LINE - font.width(label)) / HALF, y + TEXT_NUDGE, LINK);
        targets.add(boxX, boxY, LINE, LINE, () -> pressed.accept(new BuilderOverlays.Anchor(boxX, boxY)));
        x += LINE + GAP;
    }

    /**
     * Curated runs on one line, a plain left click on them running the action where one is given and a shift + left
     * click on a reference among them opening its prefab.
     * Decision shift-left-click-follows-plain-left-modifies.
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
        int inset = pressed == null ? 0 : ButtonPainter.PADDING;
        reserve(width + inset + inset);
        if (pressed != null) {
            drawRunsFrame(runs, width);
        }
        addRunsTarget(width + inset + inset, hint, pressed);
        int runsTop = ExpressionLines.lineHeight(runs) == LINE ? y : y + TEXT_NUDGE;
        ExpressionLines.draw(font, runs, x + inset, runsTop, width + 1, new ExpressionLines.Painter(graphics, icons,
                targets, pressed == null ? WHITE : LINK, LINK, ExpressionLines.followedOnShiftClick(targets, followRef)));
        x += width + inset + inset + GAP;
    }

    // Decision blue-text-options-draw-as-bordered-buttons: a clickable run is a button, its runs inside; a run
    // holding an icon takes the whole line's height.
    private void drawRunsFrame(List<ExpressionLines.Run> runs, int width) {
        ButtonPainter.drawFrame(graphics, ExpressionLines.lineHeight(runs) == LINE
                ? new ButtonPainter.Box(x, y, width + ButtonPainter.PADDING + ButtonPainter.PADDING, LINE)
                : ButtonPainter.layout(width, x, y, LINE));
    }

    // The runs' click, or their hint alone where they take none.
    private void addRunsTarget(int width, @Nullable String hint, @Nullable Consumer<BuilderOverlays.Anchor> pressed) {
        int runsX = x;
        int runsY = y;
        String hoverKey = BuilderOverlays.hintKey(hint);
        if (pressed != null) {
            targets.add(runsX, runsY, width, LINE, () -> pressed.accept(new BuilderOverlays.Anchor(runsX, runsY)),
                    hoverKey);
        } else if (hoverKey != null) {
            targets.addHover(runsX, runsY, width, LINE, hoverKey);
        }
    }
}

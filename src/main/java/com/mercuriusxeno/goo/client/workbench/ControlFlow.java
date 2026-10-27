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
    private static final int ICON_NUDGE = 1;
    private static final int INDENT = 12;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String DROPDOWN_MARKER = " ▼";
    private static final String NONE_CHOSEN = "pick one";
    private static final String NONE_CHOSEN_HINT = "Click to pick one";

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
        String hint = picked ? options.get(chosen).hint() : NONE_CHOSEN_HINT;
        control(dropdownLabel(label), hint, at -> overlays.openOptions(at, options));
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
        int inset = pressed == null ? 0 : ButtonPainter.PADDING;
        reserve(width + inset + inset);
        if (pressed != null) {
            drawRunsFrame(runs, width);
        }
        addRunsTarget(width + inset + inset, hint, pressed);
        int runsTop = ExpressionLines.lineHeight(runs) == LINE ? y : y + TEXT_NUDGE;
        ExpressionLines.draw(font, runs, x + inset, runsTop, width + 1, new ExpressionLines.Painter(graphics, icons,
                targets, pressed == null ? WHITE : LINK, LINK, (name, linkX, linkY, linkWidth) -> { }));
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
        String hoverKey = hint == null ? null : BuilderOverlays.hintKey(hint);
        if (pressed != null) {
            targets.add(runsX, runsY, width, LINE, () -> pressed.accept(new BuilderOverlays.Anchor(runsX, runsY)),
                    hoverKey);
        } else if (hoverKey != null) {
            targets.addHover(runsX, runsY, width, LINE, hoverKey);
        }
    }
}

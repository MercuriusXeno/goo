package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lays a builder's words and controls left to right across an area, a piece too wide for what is left of its line
 * starting the next, indented under the first, so no control runs off the pane; each control names what it does on
 * hover (decision expression-builder-composes-every-form-without-typing). The expression builder and the Mutators
 * rows draw through it.
 */
final class ControlFlow {
    /** A full-size flow's line, an icon's, as the Mutators rows lay. */
    static final int FULL_LINE = BuilderOverlays.LINE;
    /**
     * The expression builder's line: a goo type's 12 px icon, its frame and a pixel each side, the icons drawn at
     * their native size (decision builder-font-and-spacing-shrink-padding-untouched, as the operator ruled).
     */
    static final int LINE = ButtonPainter.HEIGHT;
    /** The scale the expression builder letters its words at. */
    static final float BUILDER_SCALE = 0.75f;
    /**
     * The scale a parenthetical's "(" and ")" draw at, bold, larger than the builder's words so the parentheticals
     * read at a glance (decision parentheses-draw-bold-and-larger).
     */
    static final float PAREN_SCALE = 1.25f;
    private static final Style BOLD = Style.EMPTY.withBold(true);
    private static final int GAP = BuilderOverlays.GAP;
    private static final int TEXT_NUDGE = 5;
    private static final int HALF = 2;
    /** A drag rail's width. */
    static final int RAIL_WIDTH = 6;
    /**
     * How far each box depth steps in from the box holding it: its border and a pixel of padding (decision
     * drag-rail-containers-keep-a-pixel-between-borders).
     */
    static final int BOX_STEP = 2;
    // Decision rails-fill-the-left-edge-of-their-box: the rail takes its box's whole left edge, flush inside the
    // border, and three grip lines mark it as a rail.
    private static final int GRIP_LINES = 3;
    private static final int GRIP_PITCH = 2;
    /** How far a rail moves the next piece: its width and a gap. */
    static final int RAIL_ADVANCE = RAIL_WIDTH + GAP;
    /** A drag rail's fill. */
    static final int RAIL_COLOR = 0xFF7080A0;
    /** The grip lines a drag rail draws. */
    static final int GRIP_COLOR = 0xFFD0D8F0;
    static final int OUTLINE_COLOR = 0x60FFFFFF;
    /** How far one indent step moves a line's left edge. */
    static final int INDENT = 12;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String DROPDOWN_MARKER = " ▼";
    private static final String NONE_CHOSEN = "pick one";

    private final GuiGraphicsExtractor graphics;
    private final Font font;
    // The words' font and scale, and the line the pieces stand on: the font's own and FULL_LINE for the Mutators
    // rows, the builder's smaller lettering and LINE for the expression builder.
    private final Lettering letters;
    private final int line;
    // How far a piece taller than the line, as a 16 px item sprite on the builder's line, reaches past it above and
    // below; the step to the next line grows by it.
    private int lineOverhang;
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
    // The room above and below each line's pieces that its boxes' borders and padding take; y is the pieces' top.
    private int pad;
    // The boxes open around the next piece: a rail opens one and closeBox ends it.
    private int openBoxes;
    // Each open box's contents as top and bottom, the innermost first: every piece drawn widens every open box's
    // contents, and a box closing widens the boxes holding it by its own bounds.
    // Decision drag-rail-containers-keep-a-pixel-between-borders.
    private final Deque<int[]> contents = new ArrayDeque<>();

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
        this(graphics, targets, icons, span, followRef, Lettering.full(font), FULL_LINE);
    }

    /**
     * A flow lettered at the expression builder's smaller scale on the builder's tighter line, its icons at their
     * native size (decision builder-font-and-spacing-shrink-padding-untouched).
     *
     * @param graphics  the frame's graphics
     * @param font      the font
     * @param targets   the frame's click areas
     * @param icons     the items' icons, null where no item draws
     * @param span      where the lines lay
     * @param followRef opens the prefab a reference run names, on a shift + left click
     * @return the flow
     */
    static ControlFlow lettered(GuiGraphicsExtractor graphics, Font font, ClickTargets targets,
                                @Nullable ItemIcons icons, Span span, Consumer<String> followRef) {
        return new ControlFlow(graphics, targets, icons, span, followRef, new Lettering(font, BUILDER_SCALE), LINE);
    }

    private ControlFlow(GuiGraphicsExtractor graphics, ClickTargets targets, @Nullable ItemIcons icons, Span span,
                        Consumer<String> followRef, Lettering letters, int line) {
        this.graphics = graphics;
        this.font = letters.font();
        this.letters = letters;
        this.line = line;
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
     * @param font    the font a label beside the builder draws in, at its own size
     * @param lineTop a builder line's top
     * @return where the label's words stand so they center on the builder's line, as the builder's own smaller
     *     words and button labels center on it
     */
    static int besideBuilderTop(Font font, int lineTop) {
        return Lettering.full(font).centeredTop(lineTop, LINE);
    }

    /**
     * Grows every line after the current one by a border and a pixel of padding above and below its pieces per box
     * depth, so the boxes drawn later nest inline, each clear of the box holding it and of the buttons inside it
     * (decision drag-rail-containers-keep-a-pixel-between-borders). The current line holds no box, as a builder's
     * kind line holds none, so it keeps its top and the labels beside it their line.
     *
     * @param depth the most boxes any piece stands inside
     */
    void padForBoxes(int depth) {
        pad = depth * BOX_STEP;
    }

    // One line to the next: the pieces' line and the pad above and below it, then a clear row where boxes pad the
    // lines, so a box on one line stands clear of a box on the next.
    private int pitch() {
        int clear = pad == 0 ? line : line + pad + pad + 1;
        return clear + lineOverhang + lineOverhang;
    }

    // Steps to the next line, forgetting what the last one reached past it.
    private void advanceLine() {
        y += pitch();
        lineOverhang = 0;
    }

    // Where the flow's words stand on its line: the font's nudge at full size, centered in the line when lettered
    // smaller.
    private int wordsTop() {
        return letters.isFull() ? y + TEXT_NUDGE : letters.centeredTop(y, line);
    }

    /**
     * @return the words' font and scale, which every width the builder measures reads
     */
    Lettering letters() {
        return letters;
    }

    /**
     * @return the y below the last line
     */
    int bottom() {
        return y + line + pad + lineOverhang;
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
            advanceLine();
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
            advanceLine();
            lineLeft = left + INDENT;
            x = lineLeft;
        }
    }

    /**
     * Starts a fresh line where pieces this wide do not fit the rest of the current one, so a unit and its room
     * open together.
     *
     * @param width the pieces' width
     */
    void fit(int width) {
        reserve(width);
    }

    /**
     * Ends the current line and leaves room under it before the next.
     *
     * @param height the room
     */
    void gapBelow(int height) {
        newLine();
        y += height;
    }

    /**
     * Leaves room before the next piece.
     *
     * @param width the room
     */
    void space(int width) {
        x += width;
    }

    // A piece inside boxes ends a clear pixel short of the innermost box's right border across lines.
    private void reserve(int width) {
        int piecesRight = openBoxes == 0 ? right : right - openBoxes * BOX_STEP - 1;
        if (x + width > piecesRight && x > lineLeft) {
            advanceLine();
            lineLeft = left + INDENT;
            x = lineLeft;
        }
    }

    /**
     * @param text  words that take no click
     * @param color their color
     */
    void text(String text, int color) {
        int width = letters.width(text);
        reserve(width);
        letters.draw(graphics, text, x, wordsTop(), color);
        markText(width);
        x += width + GAP;
    }

    /**
     * Words drawn flush against the piece before them and the piece after, no gap on either side, as a ratio's colon
     * stands between its two boxes (decision ratio-colon-takes-no-spaces).
     *
     * @param text  the words
     * @param color their color
     */
    void textBetween(String text, int color) {
        textFlush(text, color);
        x -= GAP;
    }

    /**
     * A parenthetical's "(" or ")", bold and larger than the words beside it, centered on the line; where it stands
     * taller than the line the boxes holding it grow around it (decision parentheses-draw-bold-and-larger).
     *
     * @param paren the parenthesis
     * @param color its color
     */
    void paren(String paren, int color) {
        Lettering large = parenLettering(font);
        Component bold = boldParen(paren);
        int width = large.width(bold);
        reserve(width);
        int top = large.centeredTop(y, line);
        large.draw(graphics, bold, x, top, color);
        markPiece(width, top, large.lineHeight());
        x += width + GAP;
    }

    /**
     * @param font  the font
     * @param paren a parenthesis
     * @return the width it takes drawn bold and larger, as {@link #paren} draws it
     */
    static int parenWidth(Font font, String paren) {
        return parenLettering(font).width(boldParen(paren));
    }

    private static Lettering parenLettering(Font font) {
        return new Lettering(font, PAREN_SCALE);
    }

    private static Component boldParen(String paren) {
        return Component.literal(paren).withStyle(BOLD);
    }

    /**
     * Words drawn flush against the piece before them, no gap between, as a member's 's against its prefab's button
     * (decision possessive-sits-flush-against-its-word-button).
     *
     * @param text  the words
     * @param color their color
     */
    void textFlush(String text, int color) {
        x -= GAP;
        int width = letters.width(text);
        letters.draw(graphics, text, x, wordsTop(), color);
        markText(width);
        x += width + GAP;
    }

    // A piece filling its line's height, as a goo type's button stands.
    private void markPiece(int width) {
        markPiece(width, y, line);
    }

    // A piece standing from top for height pixels, which the open boxes size around.
    private void markPiece(int width, int top, int height) {
        pieceLeft = x;
        pieceTop = y;
        pieceWidth = width;
        for (int[] box : contents) {
            box[0] = Math.min(box[0], top);
            box[1] = Math.max(box[1], top + height);
        }
    }

    // A button's piece: its frame, inset from its line's top and bottom.
    private void markButton(int width) {
        ButtonPainter.Box box = ButtonPainter.layout(0, x, y, line);
        markPiece(width, box.top(), box.height());
    }

    // Words' piece: the font's line from where the words stand.
    private void markText(int width) {
        markPiece(width, wordsTop(), letters.lineHeight());
    }

    /**
     * Lays a hover area over the piece drawn last, so it names what the hover key shows while a click passes to the
     * area under it.
     *
     * @param hoverKey what hovering the piece names
     */
    void hoverLast(String hoverKey) {
        targets.addHover(pieceLeft, pieceTop, pieceWidth, line, hoverKey);
    }

    /**
     * A line of its own, cut to the area's width.
     *
     * @param text  the note
     * @param color its color
     */
    void note(String text, int color) {
        newLine();
        String shown = letters.fitting(text, right - left);
        letters.draw(graphics, shown, x, wordsTop(), color);
        x += letters.width(shown) + GAP;
    }

    /**
     * @param label   the control's label
     * @param hint    what it does, shown on hover
     * @param pressed takes where it was clicked
     */
    void control(String label, @Nullable String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        coloredControl(label, LINK, hint, pressed);
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
        int width = ButtonPainter.width(letters, label);
        reserve(width);
        markButton(width);
        int controlX = x;
        int controlY = y;
        ButtonPainter.Box box = ButtonPainter.layout(letters.width(label), controlX, controlY, line);
        ButtonPainter.drawLink(graphics, letters, label, box, targets, new ClickTargets.LinkGestures(
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
        int width = ButtonPainter.width(letters, label);
        reserve(width);
        markButton(width);
        x = ButtonPainter.drawFollow(graphics, letters, label, new ButtonPainter.Box(x, y, 0, line), targets, follow,
                hoverKey).right() + GAP;
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
        int width = ButtonPainter.width(letters, label);
        reserve(width);
        markButton(width);
        int controlX = x;
        int controlY = y;
        ButtonPainter.Box box = ButtonPainter.drawColored(graphics, letters, label, color,
                new ButtonPainter.Box(controlX, controlY, 0, line), targets,
                () -> pressed.accept(new BuilderOverlays.Anchor(controlX, controlY)), BuilderOverlays.hintKey(hint));
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
     * Stands the next piece on the column the boxes above draw their left borders on, a pixel left of the line's
     * left edge as {@link #boxFrom} lays them, so a button opening a line lines up with the boxed lines over it
     * (decision plus-question-and-equals-align-with-the-other-lines).
     */
    void alignToBoxBorder() {
        x = lineLeft - 1;
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
            advanceLine();
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
     * @return the left edge the next lines start at
     */
    int leftEdge() {
        return left;
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
        int width = ButtonPainter.iconWidth(size);
        reserve(width);
        markPiece(width);
        int iconX = x;
        int iconY = y;
        ButtonPainter.drawFrame(graphics, new ButtonPainter.Box(iconX, iconY, width, line));
        GooTypeIcons.blit(graphics, type, iconX + ButtonPainter.ICON_PADDING,
                iconY + GooTypeIcons.centered(line, size));
        targets.add(iconX, iconY, width, line,
                () -> pressed.accept(new BuilderOverlays.Anchor(iconX, iconY)), GooTypeIcons.pickHoverKey(type));
        x += width + GAP;
    }

    /**
     * A rail's grab, opening its box, its drawing left for drawRail once its box's height is known, as a
     * parenthetical's or a type row's spans every line it takes; closeBox ends the box (decision
     * terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor). The rail hovers nothing, a control apart from the
     * thing it drags (decision drag-rails-hover-nothing).
     *
     * @param name     what the rail drags
     * @param grab     what the press, each drag and the release move
     * @param keepWith the width of the pieces that follow it, which wrap with it rather than leave it a line's end
     * @return the rail's left edge
     */
    int railTarget(String name, ClickTargets.PointerGrab grab, int keepWith) {
        reserve(RAIL_ADVANCE + keepWith);
        pieceLeft = x;
        pieceTop = y;
        pieceWidth = RAIL_WIDTH;
        contents.push(new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
        int railLeft = x;
        targets.addGrab(x, y, RAIL_WIDTH, line, grab, name);
        x += RAIL_ADVANCE;
        openBoxes++;
        return railLeft;
    }

    /**
     * Ends the box the last open rail opened, so the pieces after it take the room it held.
     */
    void closeBox() {
        openBoxes--;
        int[] closed = contents.pop();
        if (closed[0] <= closed[1]) {
            for (int[] holder : contents) {
                holder[0] = Math.min(holder[0], closed[0] - BOX_STEP);
                holder[1] = Math.max(holder[1], closed[1] + BOX_STEP);
            }
        }
    }

    /**
     * Draws a rail down its box's left edge on the box's first line, as a term's box takes that line alone.
     *
     * @param start where the rail stood
     * @param depth how many boxes hold it, its own and a type row's included
     */
    void drawRailOnLine(BuilderOverlays.Anchor start, int depth) {
        GroupsPane.Bounds box = boxFrom(start, depth);
        drawRail(start.x(), box.top() + 1, box.bottom() - 1);
    }

    /**
     * Draws a rail and its three grip lines centered in it (decision rails-fill-the-left-edge-of-their-box).
     *
     * @param left   the rail's left edge, flush inside its box's left border
     * @param top    its top, flush under the box's top border
     * @param bottom its bottom, flush over the box's bottom border
     */
    void drawRail(int left, int top, int bottom) {
        drawRail(graphics, left, top, bottom);
    }

    /**
     * Draws a rail and its three grip lines centered in it, as a group member's rail does outside any builder line.
     *
     * @param graphics the frame's graphics
     * @param left     the rail's left edge
     * @param top      its top
     * @param bottom   its bottom
     */
    static void drawRail(GuiGraphicsExtractor graphics, int left, int top, int bottom) {
        graphics.fill(left, top, left + RAIL_WIDTH, bottom, RAIL_COLOR);
        int first = (top + bottom) / HALF - GRIP_PITCH * (GRIP_LINES / HALF);
        for (int line = 0; line < GRIP_LINES; line++) {
            int gripTop = first + line * GRIP_PITCH;
            graphics.fill(left + 1, gripTop, left + RAIL_WIDTH - 1, gripTop + 1, GRIP_COLOR);
        }
    }

    /**
     * Outlines a box one pixel in the color given, as a type row's box reads around its rail and terms.
     *
     * @param box   the box
     * @param color the outline's color
     */
    void outlineBox(GroupsPane.Bounds box, int color) {
        graphics.fill(box.left(), box.top(), box.right(), box.top() + 1, color);
        graphics.fill(box.left(), box.bottom() - 1, box.right(), box.bottom(), color);
        graphics.fill(box.left(), box.top(), box.left() + 1, box.bottom(), color);
        graphics.fill(box.right() - 1, box.top(), box.right(), box.bottom(), color);
    }

    /**
     * Outlines the pieces drawn since the place given, where they stand on its line, as a term and its rail read as
     * one unit (decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     *
     * @param start where the first piece stood
     * @param depth how many boxes hold it, its own and a type row's included
     */
    void outlineFrom(BuilderOverlays.Anchor start, int depth) {
        if (start.y() != y) {
            return;
        }
        outlineBox(boxFrom(start, depth), OUTLINE_COLOR);
    }

    /**
     * Tints the box from the place given to the pieces drawn since: on one line, from there to the last piece; across
     * lines, from there to the area's right edge, a border and a pixel in per depth so a box inside a box shows its
     * own edge (decisions parentheticals-alternate-gray-tones-by-depth,
     * drag-rail-containers-keep-a-pixel-between-borders).
     *
     * @param start  where the box's first piece stood
     * @param tones  the box's fill and the border around it
     * @param depth  how many boxes hold it, its own and a type row's included
     */
    void tintFrom(BuilderOverlays.Anchor start, BoxTones tones, int depth) {
        GroupsPane.Bounds box = boxFrom(start, depth);
        int border = tones.border();
        graphics.fill(box.left() + 1, box.top(), box.right() - 1, box.top() + 1, border);
        graphics.fill(box.left() + 1, box.bottom() - 1, box.right() - 1, box.bottom(), border);
        graphics.fill(box.left(), box.top(), box.left() + 1, box.bottom(), border);
        graphics.fill(box.right() - 1, box.top(), box.right(), box.bottom(), border);
        graphics.fill(box.left() + 1, box.top() + 1, box.right() - 1, box.bottom() - 1, tones.fill());
    }

    /**
     * A box's colors (decision parentheticals-alternate-gray-tones-by-depth).
     *
     * @param fill   inside its border
     * @param border its one-pixel edge
     */
    record BoxTones(int fill, int border) {
    }

    /**
     * A box sizes to its own contents: its border and one clear pixel above the topmost piece or box it holds and
     * below the bottommost, however deep a box elsewhere nests; across lines it reaches the area's right edge, each
     * depth a border and a pixel in (decision drag-rail-containers-keep-a-pixel-between-borders). Called while the
     * box stands open, before closeBox; a box holding no piece takes its lines' padded edges.
     *
     * @param start where the box's first piece stood
     * @param depth how many boxes hold it, its own and a type row's included
     * @return the box from the place given to the pieces drawn since, as tintFrom fills it
     */
    GroupsPane.Bounds boxFrom(BuilderOverlays.Anchor start, int depth) {
        int inset = (depth - 1) * BOX_STEP;
        int boxRight = start.y() == y ? x - GAP + 1 : right - inset;
        int[] held = contents.peek();
        if (held == null || held[0] > held[1]) {
            return new GroupsPane.Bounds(start.x() - 1, start.y() - pad + inset, boxRight, y + line + pad - inset);
        }
        return new GroupsPane.Bounds(start.x() - 1, held[0] - BOX_STEP, boxRight, held[1] + BOX_STEP);
    }

    /**
     * A button whose press grabs the pointer, as a handle dragged onto a term does, a rail with its grips flush inside
     * its left border so it reads as a thing to drag (decisions parenthetical-handle-drags-onto-a-term,
     * parenthetical-handle-carries-a-rail-and-hovers-its-drop).
     *
     * @param label its label
     * @param grab  follows the pointer from the press to the release
     * @param hint  what hovering it reads, null for nothing
     */
    void grabControl(String label, ClickTargets.PointerGrab grab, @Nullable String hint) {
        int labelLeftRoom = RAIL_WIDTH + ButtonPainter.PADDING;
        int width = 1 + labelLeftRoom + letters.width(label) + ButtonPainter.PADDING;
        reserve(width);
        markButton(width);
        ButtonPainter.Box box = ButtonPainter.layout(letters.width(label), x, y, line);
        ButtonPainter.Box railed = new ButtonPainter.Box(box.left(), box.top(), width, box.height());
        ButtonPainter.drawFrame(graphics, railed);
        drawRail(railed.left() + 1, railed.top() + 1, railed.top() + railed.height() - 1);
        letters.draw(graphics, label, railed.left() + 1 + labelLeftRoom, wordsTop(), LINK);
        targets.addGrab(railed.left(), railed.top(), railed.width(), railed.height(), grab);
        String hoverKey = BuilderOverlays.hintKey(hint);
        if (hoverKey != null) {
            targets.addHover(railed.left(), railed.top(), railed.width(), railed.height(), hoverKey);
        }
        x = railed.right() + GAP;
    }

    /**
     * A square button a line's height on each side, its label centered in it, as a goo type button stands that
     * tall (decision add-type-is-a-big-square-plus-question-button-below-the-list).
     *
     * @param label   its label
     * @param hint    what hovering it reads
     * @param pressed takes where it was clicked
     */
    void squareControl(String label, String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        reserve(line);
        markPiece(line);
        int boxX = x;
        int boxY = y;
        ButtonPainter.drawFrame(graphics, new ButtonPainter.Box(boxX, boxY, line, line));
        letters.draw(graphics, label, boxX + (line - letters.width(label)) / HALF, wordsTop(), LINK);
        targets.add(boxX, boxY, line, line, () -> pressed.accept(new BuilderOverlays.Anchor(boxX, boxY)),
                BuilderOverlays.hintKey(hint));
        x += line + GAP;
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
        int width = ExpressionLines.width(letters, runs);
        int inset = pressed == null ? 0 : ButtonPainter.PADDING;
        reserve(width + inset + inset);
        RunsBand band = bandOf(runs);
        markRuns(band, width + inset + inset, pressed != null);
        if (pressed != null) {
            drawRunsFrame(band, width);
        }
        addRunsTarget(band, width + inset + inset, hint, pressed);
        ExpressionLines.draw(letters, runs, new ExpressionLines.Span(x + inset, band.top(), width + 1, band.height()),
                new ExpressionLines.Painter(graphics, icons, targets, pressed == null ? WHITE : LINK, LINK,
                        ExpressionLines.followedOnShiftClick(targets, followRef)));
        lineOverhang = Math.max(lineOverhang, y - band.top());
        x += width + inset + inset + GAP;
    }

    /**
     * Where runs draw on the flow's line.
     *
     * @param top    the band's top
     * @param height the band's height
     * @param filled whether the runs fill their band as an icon line does, rather than stand as text on the line
     */
    private record RunsBand(int top, int height, boolean filled) {
    }

    // At full size an icon line's runs fill the line and text runs stand at the words' nudge; lettered smaller, runs
    // fill the builder's line, and a 16 px item sprite's line stands taller, reaching past the line evenly above and
    // below (decision builder-font-and-spacing-shrink-padding-untouched, as the operator ruled).
    private RunsBand bandOf(List<ExpressionLines.Run> runs) {
        int natural = ExpressionLines.lineHeight(runs);
        if (letters.isFull()) {
            return natural == line ? new RunsBand(y, line, true) : new RunsBand(y + TEXT_NUDGE, natural, false);
        }
        boolean sprite = runs.stream().anyMatch(run -> run.kind() == ExpressionLines.Kind.ITEM);
        int height = sprite ? ExpressionLines.ICON_LINE_HEIGHT : line;
        return new RunsBand(y - (height - line) / HALF, height, true);
    }

    // Framed runs stand as their frame does; bare text runs as their own band.
    private void markRuns(RunsBand band, int width, boolean framed) {
        if (!band.filled() && framed) {
            markButton(width);
        } else {
            markPiece(width, band.top(), band.height());
        }
    }

    // Decision blue-text-options-draw-as-bordered-buttons: a clickable run is a button, its runs inside; runs that
    // fill their band take the band's height.
    private void drawRunsFrame(RunsBand band, int width) {
        ButtonPainter.drawFrame(graphics, band.filled()
                ? new ButtonPainter.Box(x, band.top(), width + ButtonPainter.PADDING + ButtonPainter.PADDING,
                        band.height())
                : ButtonPainter.layout(width, x, y, line));
    }

    // The runs' click, or their hint alone where they take none, over the band they fill or the line they stand on.
    private void addRunsTarget(RunsBand band, int width, @Nullable String hint,
                               @Nullable Consumer<BuilderOverlays.Anchor> pressed) {
        int runsX = x;
        int runsY = y;
        int areaTop = band.filled() ? band.top() : y;
        int areaHeight = band.filled() ? band.height() : line;
        String hoverKey = BuilderOverlays.hintKey(hint);
        if (pressed != null) {
            targets.add(runsX, areaTop, width, areaHeight,
                    () -> pressed.accept(new BuilderOverlays.Anchor(runsX, runsY)), hoverKey);
        } else if (hoverKey != null) {
            targets.addHover(runsX, areaTop, width, areaHeight, hoverKey);
        }
    }
}

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
    /**
     * The room between one builder line's pieces and the next line's, the 5 px the boxes' padding left before the
     * containers were segmented, so containers on different lines never touch (the operator's ruling of
     * 2026-11-04).
     */
    static final int BUILDER_LINE_GAP = 5;
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
    // The one clear pixel between a ratio's colon and each of its boxes.
    private static final int CLEAR = 1;
    /** A drag rail's width. */
    static final int RAIL_WIDTH = 6;
    // Decision rails-fill-the-left-edge-of-their-box: the rail fills its segment flush inside the border, and three
    // grip lines mark it as a rail.
    private static final int GRIP_LINES = 3;
    private static final int GRIP_PITCH = 2;
    /** A rail segment's width: the rail and the border either side of it. */
    static final int RAIL_SEGMENT_WIDTH = RAIL_WIDTH + 2;
    /** How far a rail moves the next piece: its width and a gap. */
    static final int RAIL_ADVANCE = RAIL_WIDTH + GAP;
    /** A drag rail's fill. */
    static final int RAIL_COLOR = 0xFF7080A0;
    /** The grip lines a drag rail draws. */
    static final int GRIP_COLOR = 0xFFD0D8F0;
    /** A rail's fill where what it holds has nowhere to drop, so it takes no press. */
    static final int LOCKED_RAIL_COLOR = 0xFF3C3C3C;
    // A locked rail's grip lines, dimmed with it.
    private static final int LOCKED_GRIP_COLOR = 0xFF5A5A5A;
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
    // The room between one line's pieces and the next line's: none for the Mutators rows, BUILDER_LINE_GAP for the
    // expression builder.
    private final int lineGap;
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
    // Each open container's contents as top and bottom, the innermost first: every piece drawn widens every open
    // container's contents, and a container closing widens the ones holding it by its own bounds.
    private final Deque<int[]> contents = new ArrayDeque<>();
    // Every rail the flow laid this frame, which paintRails draws and arms once the pane knows which can drag.
    private final List<LaidRail> laidRails = new ArrayList<>();
    // The segmented container open: its left, its top and height, and the left the next segment's border takes.
    private int segmentsLeft;
    private int segmentsTop;
    private int segmentsHeight;
    private int nextSegmentLeft;

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
        this.lineGap = letters.isFull() ? 0 : BUILDER_LINE_GAP;
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

    // Steps to the next line: the line, what its tallest piece reached past it above and below, and the room between
    // lines, forgetting that reach.
    private void advanceLine() {
        y += line + lineOverhang + lineOverhang + lineGap;
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
        return y + line + lineOverhang;
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

    private void reserve(int width) {
        if (x + width > right && x > lineLeft) {
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
     * Words drawn between the piece before them and the piece after, one clear pixel from each, as a ratio's colon
     * stands between its two boxes (decision ratio-colon-takes-no-spaces, as the operator corrected it: the space
     * reduced, not removed).
     *
     * @param text  the words
     * @param color their color
     */
    void textBetween(String text, int color) {
        x += CLEAR - GAP;
        int ink = letters.inkWidth(text);
        letters.draw(graphics, text, x, wordsTop(), color);
        markText(ink);
        x += ink + CLEAR;
    }

    /**
     * @param font  the font
     * @param paren a parenthesis
     * @return the width its segment takes: the parenthesis drawn bold and larger and a button's padding either side
     *     (decisions parentheses-draw-bold-and-larger, parens-are-segments-bounding-the-parenthetical)
     */
    static int parenSegmentWidth(Font font, String paren) {
        return parenLettering(font).width(boldParen(paren)) + ButtonPainter.PADDING + ButtonPainter.PADDING;
    }

    /**
     * A parenthetical's "(" or ")" as its container's segment, bold and larger than the words beside it, centered in
     * the segment (decisions parentheses-draw-bold-and-larger, parens-are-segments-bounding-the-parenthetical).
     *
     * @param paren the parenthesis
     * @param color its color
     */
    void parenSegment(String paren, int color) {
        ButtonPainter.Box box = nextSegment(parenSegmentWidth(font, paren));
        Lettering large = parenLettering(font);
        large.draw(graphics, boldParen(paren), box.left() + ButtonPainter.PADDING,
                large.centeredTop(box.top(), box.height()), color);
    }

    private static Lettering parenLettering(Font font) {
        return new Lettering(font, PAREN_SCALE);
    }

    private static Component boldParen(String paren) {
        return Component.literal(paren).withStyle(BOLD);
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
     * What a rail's press takes and what it drags.
     *
     * @param grab what the press, each drag and the release move
     * @param name what the rail drags
     */
    private record RailGrab(ClickTargets.PointerGrab grab, String name) {
    }

    /**
     * A rail laid down its box's left edge, waiting to be painted.
     *
     * @param left   its left edge
     * @param top    its top
     * @param bottom its bottom
     * @param grab   what its press takes
     */
    private record LaidRail(int left, int top, int bottom, RailGrab grab) {
    }

    /**
     * Paints every rail the frame laid: one whose thing may drop somewhere in its own color, its press taking the
     * whole rail as drawn, every line its box spans; one whose thing has nowhere to drop dark gray, taking no press,
     * so no rail offers a drag that does nothing. Called once the frame's drop places are laid.
     *
     * @param draggable whether the thing a rail drags, by the name it drags, has a place to drop
     */
    void paintRails(java.util.function.Predicate<String> draggable) {
        for (LaidRail rail : laidRails) {
            String name = rail.grab().name();
            if (draggable.test(name)) {
                drawRail(graphics, new RailPaint(rail.left(), rail.top(), rail.bottom(), RAIL_COLOR, GRIP_COLOR));
                targets.addGrab(rail.left(), rail.top(), RAIL_WIDTH, rail.bottom() - rail.top(), rail.grab().grab(),
                        name);
            } else {
                drawRail(graphics, new RailPaint(rail.left(), rail.top(), rail.bottom(), LOCKED_RAIL_COLOR,
                        LOCKED_GRIP_COLOR));
            }
        }
        laidRails.clear();
    }

    /**
     * Where a rail paints and in which colors.
     *
     * @param left   its left edge
     * @param top    its top
     * @param bottom its bottom
     * @param fill   its fill
     * @param grips  its grip lines' color
     */
    private record RailPaint(int left, int top, int bottom, int fill, int grips) {
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
        drawRail(graphics, new RailPaint(left, top, bottom, RAIL_COLOR, GRIP_COLOR));
    }

    private static void drawRail(GuiGraphicsExtractor graphics, RailPaint rail) {
        graphics.fill(rail.left(), rail.top(), rail.left() + RAIL_WIDTH, rail.bottom(), rail.fill());
        int first = (rail.top() + rail.bottom()) / HALF - GRIP_PITCH * (GRIP_LINES / HALF);
        for (int line = 0; line < GRIP_LINES; line++) {
            int gripTop = first + line * GRIP_PITCH;
            graphics.fill(rail.left() + 1, gripTop, rail.left() + RAIL_WIDTH - 1, gripTop + 1, rail.grips());
        }
    }

    /**
     * Opens a container whose segments and inner containers span more than one segment run, as a parenthetical's
     * "(" run, its terms and its ")" run do; closeContainer answers their union.
     */
    void openContainer() {
        contents.push(new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
    }

    /**
     * Ends the container openContainer opened: on one line it spans from its first segment to its last; across lines
     * it reaches the area's right edge. Its top and bottom are its tallest piece's, with no padding (decision
     * containers-segment-into-click-to-edit-buttons); the containers holding it size around it.
     *
     * @param first its first segment run's bounds
     * @return its bounds
     */
    GroupsPane.Bounds closeContainer(GroupsPane.Bounds first) {
        int[] held = contents.pop();
        boolean oneLine = first.top() == segmentsTop;
        int containerRight = oneLine ? x - GAP : right;
        for (int[] holder : contents) {
            holder[0] = Math.min(holder[0], held[0]);
            holder[1] = Math.max(holder[1], held[1]);
        }
        return new GroupsPane.Bounds(first.left(), held[0], containerRight, held[1]);
    }

    /**
     * Fills a container in its depth's tone, as a parenthetical's tint (decision
     * parentheticals-alternate-gray-tones-by-depth).
     *
     * @param box  the container
     * @param tone the fill
     */
    void tint(GroupsPane.Bounds box, int tone) {
        graphics.fill(box.left(), box.top(), box.right(), box.bottom(), tone);
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
        drawRail(graphics, railed.left() + 1, railed.top() + 1, railed.top() + railed.height() - 1);
        letters.draw(graphics, label, railed.left() + 1 + labelLeftRoom, wordsTop(), LINK);
        targets.addGrab(railed.left(), railed.top(), railed.width(), railed.height(), grab);
        String hoverKey = BuilderOverlays.hintKey(hint);
        if (hoverKey != null) {
            targets.addHover(railed.left(), railed.top(), railed.width(), railed.height(), hoverKey);
        }
        x = railed.right() + GAP;
    }

    /**
     * @param widths each segment's own width, its two borders included
     * @return how wide the segments stand abutted, each sharing its left border with the right border of the one
     *     before it (decision containers-segment-into-click-to-edit-buttons)
     */
    static int abutted(int... widths) {
        int total = 0;
        for (int width : widths) {
            total += width;
        }
        return widths.length == 0 ? 0 : total - (widths.length - 1);
    }

    /**
     * @param letters the words' font and scale
     * @param text    words a segment holds that take no click, as a ratio's colon
     * @return the segment's width: its glyphs, a clear pixel either side and its two borders
     */
    static int textSegmentWidth(Lettering letters, String text) {
        return 1 + CLEAR + letters.inkWidth(text) + CLEAR + 1;
    }

    /**
     * @param letters    the words' font and scale
     * @param possessive the words before the icon, as a member's 's, empty for none
     * @param type       the goo type's id
     * @return the segment's width: a type-icon button's, the icon and its border either side (decision
     *     type-icon-buttons-shrink-their-padding), widened where words stand before the icon by their glyphs, a clear
     *     pixel before them and a clear pixel either side of the icon
     */
    static int typeSegmentWidth(Lettering letters, String possessive, String type) {
        int icon = ButtonPainter.iconWidth(GooTypeIcons.sizeOf(type));
        return possessive.isEmpty() ? icon : 1 + CLEAR + letters.inkWidth(possessive) + icon + 1;
    }

    /**
     * @param letters the words' font and scale
     * @param runs    curated runs a segment holds
     * @return the segment's width: the runs and a button's padding either side
     */
    static int runsSegmentWidth(Lettering letters, List<ExpressionLines.Run> runs) {
        return ExpressionLines.width(letters, runs) + ButtonPainter.PADDING + ButtonPainter.PADDING;
    }

    /**
     * @param runs runs a segmented container holds
     * @return the height the container stands: the builder's line, or a 16 px item sprite's taller line
     */
    int segmentsHeight(List<ExpressionLines.Run> runs) {
        return runs.stream().anyMatch(run -> run.kind() == ExpressionLines.Kind.ITEM) ? ExpressionLines.ICON_LINE_HEIGHT
                : line;
    }

    /**
     * Opens a segmented container: its elements draw as abutting buttons sharing their borders, no outline or padding
     * around them, each as tall as the container, which centers on the line (decision
     * containers-segment-into-click-to-edit-buttons). A container too wide for the rest of the line starts the next.
     *
     * @param width  the segments' width abutted
     * @param height the container's height, the tallest segment's
     */
    void openSegments(int width, int height) {
        reserve(width);
        segmentsLeft = x;
        segmentsHeight = height;
        segmentsTop = y - (height - line) / HALF;
        nextSegmentLeft = x;
        lineOverhang = Math.max(lineOverhang, y - segmentsTop);
    }

    // The next segment's frame, its left border the right border of the one before.
    private ButtonPainter.Box nextSegment(int width) {
        ButtonPainter.Box box = new ButtonPainter.Box(nextSegmentLeft, segmentsTop, width, segmentsHeight);
        ButtonPainter.drawFrame(graphics, box);
        nextSegmentLeft = box.right() - 1;
        return box;
    }

    /**
     * @return where a drop slot stands on the right border of the segment drawn last, its cursor drawn a half gap
     *     left of the slot, on that border
     */
    BuilderOverlays.Anchor afterSegment() {
        return new BuilderOverlays.Anchor(nextSegmentLeft + GAP / HALF, y);
    }

    /**
     * A rail as the container's segment, its press dragging the whole container; it draws once the frame knows
     * whether the thing it drags has a place to drop (decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor).
     *
     * @param name what the rail drags
     * @param grab what the press, each drag and the release move
     */
    void railSegment(String name, ClickTargets.PointerGrab grab) {
        ButtonPainter.Box box = nextSegment(RAIL_SEGMENT_WIDTH);
        laidRails.add(new LaidRail(box.left() + 1, box.top() + 1, box.top() + box.height() - 1,
                new RailGrab(grab, name)));
    }

    /**
     * A segment whose label centers in it and whose click edits the element it names.
     *
     * @param label   its label
     * @param hint    what it does, shown on hover
     * @param pressed takes where it was clicked
     */
    void labelSegment(String label, @Nullable String hint, Consumer<BuilderOverlays.Anchor> pressed) {
        ButtonPainter.Box box = nextSegment(ButtonPainter.width(letters, label));
        ButtonPainter.drawLabel(graphics, letters, label, box, LINK);
        ButtonPainter.reserve(targets, box, () -> pressed.accept(new BuilderOverlays.Anchor(box.left(), y)),
                BuilderOverlays.hintKey(hint));
    }

    /**
     * A segment holding words that take no click, a clear pixel either side of their glyphs, as a ratio's colon
     * stands between its two boxes (decision ratio-colon-takes-no-spaces, as the operator corrected it).
     *
     * @param text  the words
     * @param color their color
     */
    void textSegment(String text, int color) {
        ButtonPainter.Box box = nextSegment(textSegmentWidth(letters, text));
        letters.draw(graphics, text, box.left() + 1 + CLEAR, wordsTop(), color);
    }

    /**
     * A segment holding a goo type's icon, words before it where given, as a member's 's and its type or a type
     * row's goo type, whose click edits the type.
     *
     * @param possessive the words before the icon, empty for none
     * @param type       the goo type's id
     * @param pressed    takes where it was clicked
     */
    void typeSegment(String possessive, String type, Consumer<BuilderOverlays.Anchor> pressed) {
        ButtonPainter.Box box = nextSegment(typeSegmentWidth(letters, possessive, type));
        int iconLeft = box.left() + ButtonPainter.ICON_PADDING;
        if (!possessive.isEmpty()) {
            int wordsLeft = box.left() + 1 + CLEAR;
            letters.draw(graphics, possessive, wordsLeft, wordsTop(), WHITE);
            iconLeft = wordsLeft + letters.inkWidth(possessive) + ButtonPainter.ICON_PADDING;
        }
        int size = GooTypeIcons.sizeOf(type);
        GooTypeIcons.blit(graphics, type, iconLeft, box.top() + GooTypeIcons.centered(box.height(), size));
        targets.add(box.left(), box.top(), box.width(), box.height(),
                () -> pressed.accept(new BuilderOverlays.Anchor(box.left(), y)), GooTypeIcons.pickHoverKey(type));
    }

    /**
     * Curated runs as a segment, a plain left click on it running the action where one is given and a shift + left
     * click on a reference among them opening its prefab (decision shift-left-click-follows-plain-left-modifies).
     *
     * @param runs    the runs
     * @param pressed takes where they were clicked, null for no click
     */
    void runsSegment(List<ExpressionLines.Run> runs, @Nullable Consumer<BuilderOverlays.Anchor> pressed) {
        ButtonPainter.Box box = nextSegment(runsSegmentWidth(letters, runs));
        if (pressed != null) {
            targets.add(box.left(), box.top(), box.width(), box.height(),
                    () -> pressed.accept(new BuilderOverlays.Anchor(box.left(), y)), null);
        }
        ExpressionLines.draw(letters, runs, new ExpressionLines.Span(box.left() + ButtonPainter.PADDING, box.top(),
                ExpressionLines.width(letters, runs) + 1, box.height()), new ExpressionLines.Painter(graphics, icons,
                targets, pressed == null ? WHITE : LINK, LINK, ExpressionLines.followedOnShiftClick(targets, followRef)));
    }

    /**
     * Ends the segmented container, the next piece standing a gap past it; the boxes holding it size around it.
     *
     * @return the container's bounds, the union of its segments
     */
    GroupsPane.Bounds closeSegments() {
        int containerRight = nextSegmentLeft + 1;
        int bottom = segmentsTop + segmentsHeight;
        for (int[] box : contents) {
            box[0] = Math.min(box[0], segmentsTop);
            box[1] = Math.max(box[1], bottom);
        }
        x = containerRight + GAP;
        return new GroupsPane.Bounds(segmentsLeft, segmentsTop, containerRight, bottom);
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

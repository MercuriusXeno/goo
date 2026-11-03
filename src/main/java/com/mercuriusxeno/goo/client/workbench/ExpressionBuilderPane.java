package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Draws the expression builder over a value's curated view (decisions
 * expression-builder-composes-every-form-without-typing, goo-type-picker-is-a-seven-by-four-icon-grid). Its first
 * line chooses the value's kind: a number (a prefab's alone), an amount per goo type, or a goo value summed from
 * other items or prefabs. Each control offers only what the game's evaluator accepts where the value is read, as
 * {@link BuilderGrammar} answers, so no choice ends in an error: a number stands unscaled, a reference draws its
 * factor as a ratio, the goo type picker hides the types a value holds, and a list holds only what the value may read. A
 * parenthetical the file holds draws between a "(" line and a ")" line with its terms indented, each edited in place,
 * and the whole value scales as one sum; these are the two ways the game's files use parentheses. Every control names what
 * it does on hover, the lines wrap inside the pane, and done closes the builder below the value. Each change
 * writes the string the file grammar reads; a value is never written empty. The state lives in
 * {@link ExpressionBuilder}.
 */
final class ExpressionBuilderPane {
    private static final int GRAY = 0xFFA0A0A0;
    /** An unreadable value's note and a remove control's X. */
    static final int RED = 0xFFFF5555;
    /** The done control's check mark and word. */
    static final int GREEN = 0xFF80E080;
    // Decision parentheticals-alternate-gray-tones-by-depth: a parenthetical's box reads as a box, its border clearly
    // off its fill, and each box keeps room inside its outline and between it and the next.
    /** A parenthetical box's border. */
    static final int NEST_BORDER = 0xC0A8B4D0;
    /** The room from a box's border to its rail. */
    static final int BOX_PADDING = 5;
    /** The room between sibling term boxes. */
    static final int TERM_MARGIN = 8;
    // The room a box adds inside its border past what the flow leaves, and past its last piece.
    private static final int BOX_INSIDE = BOX_PADDING - ControlFlow.RAIL_MARGIN - 1;
    // The room a box adds after itself past the gap the flow leaves, less the two border pixels.
    private static final int BOX_AFTER = TERM_MARGIN - BuilderOverlays.GAP + 2;
    /** The builder's panel fill. */
    static final int PANEL_COLOR = 0x30FFFFFF;
    // Decision terms-and-type-rows-drag-to-a-trash-strip: the strip along the builder's right edge, dim at rest,
    // lit while a drag is in flight and brighter while the dragged thing hovers it.
    /** The trash strip's width. */
    static final int TRASH_WIDTH = 12;
    /** The trash strip's tone at rest. */
    static final int TRASH_REST = 0x30FF5555;
    /** The trash strip's tone while a drag is in flight. */
    static final int TRASH_LIT = 0x80FF5555;
    /** The trash strip's tone while the dragged thing hovers it. */
    static final int TRASH_HOVER = 0xD0FF5555;
    private static final String NUMBER_KIND = "a number";
    private static final String PER_TYPE = "by type";
    // Decision items-kind-hint-says-worth-based-on-other-items-prefabs: the goo kind reads relative on every tab.
    private static final String GOO_KIND = "relative";
    // Decision amount-modes-name-what-the-amount-comes-from.
    private static final String NUMBER_MODE = "number";
    private static final String PREFAB_MODE = "prefab";
    private static final String RELATIVE_MODE = "relative";
    // Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators.
    private static final String PLUS_SIGN = "+";
    private static final String MINUS_SIGN = "-";
    private static final String SUM_ADD = "+/-";
    private static final String POSSESSIVE = "'s";
    private static final String WRAP = "()";
    /** The drop cursor's blue. */
    static final int DROP_CURSOR = 0xFF4090FF;
    /** The cursor's blue, see-through, over the term the held () would wrap. */
    static final int WRAP_MARK = 0x804090FF;
    private static final String RAIL_HOVER_PREFIX = "goo-term-rail:";
    private static final String ROW_RAIL_HOVER_PREFIX = "goo-row-rail:";
    private static final int HALF = 2;
    private static final int NEST_DARKER = 0x28000000;
    private static final int NEST_LIGHTER = 0x28FFFFFF;
    // A pixel off the slot's line counts this many off along it, so the cursor keeps to the line the pointer is on.
    private static final int LINE_WEIGHT = 4;
    // The drop cursor shows for this many frames, then hides for as many.
    private static final int BLINK_FRAMES = 20;
    private static final int CURSOR_INSET = 2;
    private static final int PREVIEW_OFFSET = 8;
    private static final int PREVIEW_COLOR = 0xE0182030;
    private static final int PREVIEW_TEXT = 0xFFFFFFFF;
    private static final String RATIO = ":";
    private static final String WHOLE_VALUE = "the whole value";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String ADD_TYPE = "+?";
    private static final String UNREAD = "This value cannot be read; pick a kind to start over.";
    private static final String DENIED = "Denied: this holds no goo. Pick a kind to give it a value.";
    // decision done-draws-green-with-a-check-mark
    private static final String DONE = "✓ Done";
    private static final String BLANK = "";
    // Decision value-expression-boxes-take-a-cancel-button.
    private static final String CANCEL = "✗ Cancel";

    private final ManagerActions actions;
    private final ItemIcons icons;
    private final BuilderOverlays overlays;
    private final Supplier<List<ValuesTab.ManagerEntry>> inventory;
    private final Supplier<List<BuilderGrammar.Prefab>> prefabs;
    private BuilderGrammar grammar;
    private @Nullable ExpressionBuilder builder;
    // The drop slots the last frame laid, and the term a rail holds while it drags.
    private final List<Slot> slots = new ArrayList<>();
    private @Nullable Drag dragging;
    // The term boxes the last frame laid, and the () handle while it drags.
    private final List<TermBox> boxes = new ArrayList<>();
    private @Nullable Wrapping wrapping;
    // The row slots the last frame laid, and the type row a rail holds while it drags.
    private final List<RowSlot> rowSlots = new ArrayList<>();
    private @Nullable RowDrag rowDragging;
    // The trash strip the last frame drew.
    private GroupsPane.@Nullable Bounds trash;
    private int frames;
    private String openedText = BLANK;
    private String openedShown = BLANK;
    private @Nullable Consumer<String> writes;
    private @Nullable Runnable done;

    /**
     * @param actions   the panes' line back to the screen
     * @param icons     the items' icons
     * @param inventory the server's items, which item terms and amounts list
     * @param prefabs   every prefab, in the file's order
     */
    ExpressionBuilderPane(ManagerActions actions, ItemIcons icons, Supplier<List<ValuesTab.ManagerEntry>> inventory,
                          Supplier<List<BuilderGrammar.Prefab>> prefabs) {
        this.actions = actions;
        this.icons = icons;
        this.overlays = new BuilderOverlays(actions, icons);
        this.inventory = inventory;
        this.prefabs = prefabs;
        this.grammar = BuilderGrammar.forItems(List.of());
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        overlays.init(font);
    }

    /**
     * Opens the builder over an item's or group's value, which reads items and every prefab.
     *
     * @param shown  the value as the draft's edit box shows it
     * @param change takes the string each change writes
     * @param finish runs when done closes the builder
     */
    void open(String shown, Consumer<String> change, Runnable finish) {
        openUnder(BuilderGrammar.forItems(prefabs.get()), shown, change, finish);
    }

    /**
     * Opens the builder over a prefab's value, which reads only the prefabs above it.
     *
     * @param prefab the prefab's name
     * @param shown  the value as the draft's edit box shows it
     * @param change takes the string each change writes
     * @param finish runs when done closes the builder
     */
    void openPrefab(String prefab, String shown, Consumer<String> change, Runnable finish) {
        openUnder(BuilderGrammar.forPrefab(prefabs.get(), prefab), shown, change, finish);
    }

    private void openUnder(BuilderGrammar read, String shown, Consumer<String> change, Runnable finish) {
        overlays.close();
        grammar = read;
        ExpressionBuilder opened = ExpressionBuilder.of(shown);
        if (!opened.isPerType() && !opened.terms().isEmpty()) {
            opened.nameWholeSort(read.sortOf(opened));
        }
        // Decision every-value-builder-reads-value-kind-on-one-line-defaulting-by-type: an empty value opens by
        // type, so no kind reads unchosen.
        if (shown.isBlank()) {
            opened.switchToPerType();
        }
        builder = opened;
        // The builder's own text, not the shown string, so its normalized rewrite reads as no change.
        openedText = opened.text();
        openedShown = shown;
        writes = change;
        done = finish;
    }

    /**
     * Closes the builder, keeping what it wrote.
     */
    void close() {
        overlays.close();
        builder = null;
        dragging = null;
        wrapping = null;
        rowDragging = null;
    }

    /**
     * @return whether the builder stands open
     */
    boolean isOpen() {
        return builder != null;
    }

    /**
     * @param shown whether the builder's tab is the one drawn
     */
    void show(boolean shown) {
        overlays.show(shown);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the builder took the key
     */
    boolean keyPressed(int key) {
        // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor: Escape drops the held term back.
        // Decision parenthetical-handle-drags-onto-a-term: Escape drops the held () and wraps nothing.
        // Decision type-rows-drag-by-a-rail-to-reorder-or-trash: Escape drops the held row back.
        if (key == GLFW.GLFW_KEY_ESCAPE && (dragging != null || wrapping != null || rowDragging != null)) {
            dragging = null;
            wrapping = null;
            rowDragging = null;
            return true;
        }
        return overlays.keyPressed(key);
    }

    /**
     * @param path a term's place
     * @return the hover key its drag rail answers with, which names no tooltip
     */
    static String railHoverKey(List<Integer> path) {
        return RAIL_HOVER_PREFIX + path;
    }

    /**
     * @param row a type row's index
     * @return the hover key its drag rail answers with, which names no tooltip
     */
    static String rowRailHoverKey(int row) {
        return ROW_RAIL_HOVER_PREFIX + row;
    }

    /**
     * @param depth how many parentheticals hold the box, its own included, from 1
     * @return the tint its box fills in
     */
    static int nestTone(int depth) {
        // Decision parentheticals-alternate-gray-tones-by-depth: an odd depth darkens its parent's tone a step and an
        // even one lightens it back, so each box reads off the one around it.
        return depth % HALF == 0 ? NEST_LIGHTER : NEST_DARKER;
    }

    // The slot the last frame laid nearest the pointer, null where it laid none.
    private @Nullable Slot nearestSlot(double x, double y) {
        return slots.stream().min(java.util.Comparator.comparingDouble(slot -> slot.distanceTo(x, y))).orElse(null);
    }

    /**
     * @param amount the wheel's turn
     * @return whether the builder's open grid took the turn
     */
    boolean scrollList(double amount) {
        return builder != null && overlays.scrollList(amount);
    }

    /**
     * Draws the builder at a top edge across an area's width; its lists and grid draw later, through
     * {@link #drawOverlays}.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param area     the area the builder spans
     * @param top      the builder's top edge
     * @param targets  the frame's click areas
     * @return the height the builder took
     */
    int extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds area, int top, ClickTargets targets) {
        ExpressionBuilder edited = builder;
        if (edited == null) {
            return 0;
        }
        overlays.commitOnBlur();
        frames++;
        slots.clear();
        boxes.clear();
        rowSlots.clear();
        int stripLeft = area.right() - TRASH_WIDTH;
        ControlFlow flow = new ControlFlow(graphics, font, targets, icons,
                new ControlFlow.Span(area.left(), stripLeft - BuilderOverlays.GAP, top), actions::openVariable);
        drawKind(flow, edited);
        drawValue(flow, edited);
        drawClosing(flow, edited);
        int bottom = flow.bottom();
        graphics.fill(area.left() - 1, top - 1, area.right(), bottom, PANEL_COLOR);
        trash = new GroupsPane.Bounds(stripLeft, top - 1, area.right(), bottom);
        drawTrash(graphics, trash);
        drawDropCursor(graphics);
        drawRowCursor(graphics, stripLeft - BuilderOverlays.GAP);
        drawWrapMark(graphics);
        return bottom - top;
    }

    // Decision terms-and-type-rows-drag-to-a-trash-strip.
    private void drawTrash(GuiGraphicsExtractor graphics, GroupsPane.Bounds strip) {
        int tone = dragOverTrash() ? TRASH_HOVER : TRASH_REST;
        if (tone == TRASH_REST && (dragging != null || rowDragging != null)) {
            tone = TRASH_LIT;
        }
        graphics.fill(strip.left(), strip.top(), strip.right(), strip.bottom(), tone);
    }

    // Whether the term or type row held stands over the trash strip.
    private boolean dragOverTrash() {
        Drag term = dragging;
        RowDrag row = rowDragging;
        return term != null && term.overTrash || row != null && row.overTrash;
    }

    // A blinking blue bar along the row slot the held type row would drop into.
    // Decision type-rows-drag-by-a-rail-to-reorder-or-trash.
    private void drawRowCursor(GuiGraphicsExtractor graphics, int right) {
        RowDrag held = rowDragging;
        if (held == null || held.target == null || frames / BLINK_FRAMES % HALF != 0) {
            return;
        }
        BuilderOverlays.Anchor at = held.target.at();
        graphics.fill(at.x(), at.y() - 1, right, at.y() + 1, DROP_CURSOR);
    }

    // Whether the pointer stands over the trash strip the last frame drew.
    private boolean overTrash(double x, double y) {
        GroupsPane.Bounds strip = trash;
        if (strip == null) {
            return false;
        }
        return x >= strip.left() && x < strip.right() && y >= strip.top() && y < strip.bottom();
    }

    // A blinking blue bar at the slot the held term would drop into.
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawDropCursor(GuiGraphicsExtractor graphics) {
        Drag held = dragging;
        if (held == null || held.target == null || frames / BLINK_FRAMES % HALF != 0) {
            return;
        }
        BuilderOverlays.Anchor at = held.target.at();
        int x = at.x() - BuilderOverlays.GAP / HALF;
        graphics.fill(x - 1, at.y() + CURSOR_INSET, x + 1, at.y() + ControlFlow.LINE - CURSOR_INSET, DROP_CURSOR);
    }

    // The held term drawn at the pointer, over everything else.
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawHeldTerm(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        Drag held = dragging;
        if (held == null) {
            return;
        }
        drawPreview(new Preview(graphics, font, targets), operandRuns(held.piece), held.x, held.y);
    }

    // The held type row drawn at the pointer, its goo icon and colon, over everything else.
    // Decision type-rows-drag-by-a-rail-to-reorder-or-trash.
    private void drawHeldRow(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        RowDrag held = rowDragging;
        if (held == null) {
            return;
        }
        drawPreview(new Preview(graphics, font, targets), List.of(
                new ExpressionLines.Run(ExpressionLines.Kind.TYPE, BLANK, held.type),
                new ExpressionLines.Run(ExpressionLines.Kind.OPERATOR, ExpressionLines.COLON, null)), held.x, held.y);
    }

    /**
     * Where a held piece's preview draws.
     */
    private record Preview(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
    }

    private void drawPreview(Preview preview, List<ExpressionLines.Run> runs, double pointerX, double pointerY) {
        GuiGraphicsExtractor graphics = preview.graphics();
        Font font = preview.font();
        ClickTargets targets = preview.targets();
        int left = (int) pointerX + PREVIEW_OFFSET;
        int top = (int) pointerY;
        int width = ExpressionLines.width(font, runs) + ButtonPainter.PADDING + ButtonPainter.PADDING;
        int height = ExpressionLines.lineHeight(runs);
        graphics.fill(left, top, left + width, top + height, PREVIEW_COLOR);
        ExpressionLines.draw(font, runs, left + ButtonPainter.PADDING, top, width, new ExpressionLines.Painter(
                graphics, icons, targets, PREVIEW_TEXT, ButtonPainter.LINK, (link, x, y, linkWidth) -> { }));
    }

    /**
     * Draws the builder's open list or grid above everything the pane drew; the pane calls it once its own clip is
     * lifted.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param targets  the frame's click areas
     */
    void drawOverlays(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        if (builder != null) {
            overlays.draw(graphics, font, targets);
            drawHeldTerm(graphics, font, targets);
            drawHeldRow(graphics, font, targets);
            drawHeldHandle(graphics, font);
        }
    }

    /**
     * @return whether the builder's list or grid stands open, so nothing else draws over it
     */
    boolean listOpen() {
        return builder != null && overlays.listOpen();
    }

    // Decision value-expression-boxes-take-a-cancel-button: done draws only once a change is fully in, and cancel
    // always draws, so a partial or mistaken edit is always one click from discarded.
    // Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators: Cancel then Done at the last line's
    // right end.
    private void drawClosing(ControlFlow flow, ExpressionBuilder edited) {
        boolean changed = edited.written().filter(written -> !written.equals(openedText)).isPresent();
        int width = ButtonPainter.width(flow.font(), CANCEL);
        if (changed) {
            width += BuilderOverlays.GAP + ButtonPainter.width(flow.font(), DONE);
        }
        flow.alignRight(width);
        flow.coloredControl(CANCEL, RED, null, at -> cancel());
        if (changed) {
            flow.coloredControl(DONE, GREEN, null, at -> finish());
        }
    }

    // The rows or the terms, or why there are none: denied, empty, or unreadable.
    private void drawValue(ControlFlow flow, ExpressionBuilder edited) {
        if (edited.isPerType()) {
            drawRows(flow, edited);
        } else if (edited.terms().isEmpty()) {
            drawNoTerms(flow, edited);
        } else {
            drawTerms(flow, edited);
        }
    }

    private static void drawNoTerms(ControlFlow flow, ExpressionBuilder edited) {
        if (edited.isDenied()) {
            flow.note(DENIED, GRAY);
        } else {
            flow.note(UNREAD, RED);
        }
    }

    // A number is a prefab's kind alone, and a goo value is offered only where the value has something to read; a
    // per-type value's [+] after the kind appends a type row at the end.
    // Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators.
    private void drawKind(ControlFlow flow, ExpressionBuilder edited) {
        boolean perType = edited.isPerType();
        String sort = perType || edited.terms().isEmpty() ? BLANK : grammar.sortOf(edited);
        ControlFlow.ChoiceSet kinds = new ControlFlow.ChoiceSet();
        if (grammar.offersNumberKind()) {
            kinds.add(new BuilderOverlays.Option(NUMBER_KIND, null,
                    at -> apply(ExpressionBuilder::switchToWhole)), BuilderGrammar.NUMBER_SORT.equals(sort));
        }
        kinds.add(new BuilderOverlays.Option(PER_TYPE, null, at -> apply(ExpressionBuilder::switchToPerType)),
                perType);
        if (grammar.offersGooKind()) {
            kinds.add(new BuilderOverlays.Option(GOO_KIND, null, at -> switchToGoo(at, edited)),
                    BuilderGrammar.GOO_SORT.equals(sort));
        }
        flow.choices(kinds, overlays);
    }

    // A square [+?] the height of a type row's goo type button, on its own line one indent in under the rows.
    // Decision add-type-is-a-big-square-plus-question-button-below-the-list.
    // Decision parenthetical-handle-drags-onto-a-term: the () handle stands beside it.
    private void drawAddType(ControlFlow flow, ExpressionBuilder edited) {
        Set<String> held = BuilderGrammar.heldTypes(edited);
        flow.indent();
        rowSlots.add(new RowSlot(edited.rows().size(), flow.anchor()));
        if (held.size() < GooTypes.order().size()) {
            flow.squareControl(ADD_TYPE, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type)),
                    null, held));
        }
        drawHandle(flow);
        flow.outdent(ControlFlow.INDENT);
    }

    // A goo value starts from what it held before, or from the item or prefab picked first.
    private void switchToGoo(BuilderOverlays.Anchor at, ExpressionBuilder edited) {
        if (edited.holdsKept(BuilderGrammar.GOO_SORT)) {
            apply(b -> b.switchToWhole(BuilderGrammar.GOO_SORT, List.of()));
            return;
        }
        overlays.openDropdown(at, termList(BuilderGrammar.GOO_SORT), choice -> apply(b -> b.switchToWhole(
                BuilderGrammar.GOO_SORT, List.of(ExpressionBuilder.firstTerm(refOf(choice))))));
    }

    /**
     * Where a sum stands: the whole value's sort, or the goo type of the row it amounts to.
     *
     * @param sort    the whole terms' sort, read where no row holds the sum
     * @param rowType the row's goo type, null for the whole value
     */
    private record Place(String sort, @Nullable String rowType) {
        boolean inRow() {
            return rowType != null;
        }
    }

    // Each row one indent in: its rail, [type]: then its terms inline, wrapping one indent further in; a row slot
    // stands before each row and before [+?].
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    // Decision type-rows-drag-by-a-rail-to-reorder-or-trash: the rail left of the goo icon, and no x, the trash
    // strip deleting the row.
    private void drawRows(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Row> rows = edited.rows();
        Set<String> held = BuilderGrammar.heldTypes(edited);
        for (int index = 0; index < rows.size(); index++) {
            ExpressionBuilder.Row row = rows.get(index);
            int at = index;
            flow.indent();
            rowSlots.add(new RowSlot(index, flow.anchor()));
            flow.rail(rowRailHoverKey(index), new RowDrag(index, row.type()), typeLeadWidth(flow.font(), row.type()));
            flow.typeIcon(row.type(), anchor -> overlays.openPicker(anchor,
                    type -> apply(b -> b.setRowType(at, type)), null, held));
            flow.text(ExpressionLines.COLON, GRAY);
            drawSum(flow, List.of(index), row.pieces(), new Place(BuilderGrammar.NUMBER_SORT, row.type()));
            flow.outdent(ControlFlow.INDENT);
        }
        drawAddType(flow, edited);
    }

    // The goo type button and colon after a row's rail, which the rail keeps on its line.
    private static int typeLeadWidth(Font font, String type) {
        return GooTypeIcons.sizeOf(type) + ButtonPainter.PADDING + ButtonPainter.PADDING + BuilderOverlays.GAP
                + font.width(ExpressionLines.COLON);
    }

    /**
     * Where a dragged type row would drop.
     *
     * @param index the slot: before the row at that index, or the rows' count for the end
     * @param at    where the slot stands, the row's first piece
     */
    private record RowSlot(int index, BuilderOverlays.Anchor at) {
        // A slot stands at its row's top edge, so the pointer on a row's upper half drops before it.
        double distanceTo(double y) {
            return Math.abs(y - at.y());
        }
    }

    // The row slot the last frame laid nearest the pointer's line, null where it laid none.
    private @Nullable RowSlot nearestRowSlot(double y) {
        return rowSlots.stream().min(java.util.Comparator.comparingDouble(slot -> slot.distanceTo(y))).orElse(null);
    }

    /**
     * A type row held by its rail: which row, its type, where the pointer stands and the row slot it would drop into,
     * or the trash strip (decision type-rows-drag-by-a-rail-to-reorder-or-trash).
     */
    private final class RowDrag implements ClickTargets.PointerGrab {
        private final int row;
        private final String type;
        private double x;
        private double y;
        private @Nullable RowSlot target;
        private boolean overTrash;

        RowDrag(int row, String type) {
            this.row = row;
            this.type = type;
        }

        @Override
        public void moved(double movedX, double movedY) {
            rowDragging = this;
            x = movedX;
            y = movedY;
            overTrash = overTrash(x, y);
            target = overTrash ? null : nearestRowSlot(y);
        }

        @Override
        public void released() {
            RowSlot dropped = target;
            boolean held = rowDragging == this;
            rowDragging = null;
            if (held && overTrash) {
                apply(b -> b.removeRow(row));
            } else if (held && dropped != null && !isOrigin(dropped)) {
                apply(b -> b.moveRow(row, dropped.index()));
            }
        }

        // The slots either side of the row drop it where it stands.
        private boolean isOrigin(RowSlot slot) {
            return slot.index() == row || slot.index() == row + 1;
        }
    }

    // The whole value's terms on the line after the kind, one indent in.
    private void drawTerms(ControlFlow flow, ExpressionBuilder edited) {
        String sort = grammar.sortOf(edited);
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        flow.indent();
        drawSum(flow, List.of(), pieces, new Place(sort, null));
        // Decision parenthetical-handle-drags-onto-a-term: the () handle below the terms, where [+?] stands for rows.
        flow.indent(0);
        drawHandle(flow);
        flow.outdent(ControlFlow.INDENT);
        // Decision scale-shows-the-ratio-at-once: several terms draw the whole value's ratio 1:1 on a line of its
        // own; setting a side wraps the value as one sum scaled by it.
        if (pieces.size() > 1) {
            flow.newLine();
            flow.text(WHOLE_VALUE, GRAY);
            drawRatio(flow, ExpressionBuilder.ONE_TEXT, ExpressionBuilder.ONE_TEXT,
                    (op, factor) -> apply(b -> b.scaleWhole(op, factor)));
        }
    }

    // A sum's terms inline, then the [+/-] that adds one, each wrapping where the line runs out; a drop slot stands
    // before each term and after the last.
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawSum(ControlFlow flow, List<Integer> sum, List<ExpressionBuilder.Piece> pieces, Place place) {
        for (int index = 0; index < pieces.size(); index++) {
            slots.add(new Slot(sum, index, flow.anchor()));
            drawTerm(flow, placeOf(sum, index), pieces.get(index), place);
        }
        slots.add(new Slot(sum, pieces.size(), flow.anchor()));
        drawSumAdd(flow, sum, place);
    }

    /**
     * Where a dragged term would drop.
     *
     * @param sum   the sum it drops into
     * @param index the slot in it: before the term at that index, or the sum's size for its end
     * @param at    where the slot stands, the next piece's place on its line
     */
    private record Slot(List<Integer> sum, int index, BuilderOverlays.Anchor at) {
        double distanceTo(double x, double y) {
            double lineMiss = Math.abs(y - (at.y() + ControlFlow.LINE / (double) HALF)) * LINE_WEIGHT;
            return lineMiss + Math.abs(x - at.x());
        }
    }

    /**
     * A term held by its rail: where it came from, what it reads, where the pointer stands and the slot it would
     * drop into, or the trash strip.
     */
    private final class Drag implements ClickTargets.PointerGrab {
        private final List<Integer> from;
        private final ExpressionBuilder.Piece piece;
        private double x;
        private double y;
        private @Nullable Slot target;
        private boolean overTrash;

        Drag(List<Integer> from, ExpressionBuilder.Piece piece) {
            this.from = from;
            this.piece = piece;
        }

        @Override
        public void moved(double movedX, double movedY) {
            dragging = this;
            x = movedX;
            y = movedY;
            overTrash = overTrash(x, y);
            target = overTrash ? null : nearestSlot(x, y);
        }

        // Decision terms-and-type-rows-drag-to-a-trash-strip: a release on the strip deletes the term.
        @Override
        public void released() {
            Slot dropped = target;
            boolean held = dragging == this;
            dragging = null;
            if (held && overTrash) {
                apply(b -> b.remove(from));
            } else if (held && dropped != null && !isOrigin(dropped)) {
                apply(b -> b.moveTo(from, dropped.sum(), dropped.index()));
            }
        }

        // The slots either side of the term drop it where it stands.
        private boolean isOrigin(Slot slot) {
            int index = from.getLast();
            return slot.sum().equals(from.subList(0, from.size() - 1))
                    && (slot.index() == index || slot.index() == index + 1);
        }
    }

    // The sign picked joins the term added: an item or prefab picked next, or a 1 where the sum reads only numbers.
    private void drawSumAdd(ControlFlow flow, List<Integer> sum, Place place) {
        if (!hasTermChoices(place) && !takesNumbers(place)) {
            return;
        }
        List<BuilderOverlays.Option> signs = List.of(
                new BuilderOverlays.Option(PLUS_SIGN, null, at -> addTerm(at, sum, ExpressionTerms.Join.PLUS, place)),
                new BuilderOverlays.Option(MINUS_SIGN, null,
                        at -> addTerm(at, sum, ExpressionTerms.Join.MINUS, place)));
        flow.control(ControlFlow.dropdownLabel(SUM_ADD), null, at -> overlays.openOptions(at, signs));
    }

    private void addTerm(BuilderOverlays.Anchor at, List<Integer> sum, ExpressionTerms.Join join, Place place) {
        if (hasTermChoices(place)) {
            overlays.openDropdown(at, termList(place), choice -> apply(b -> b.add(sum, refFor(choice, place),
                    join)));
        } else {
            apply(b -> b.add(sum, new ExpressionBuilder.Num(ExpressionBuilder.ONE_TEXT), join));
        }
    }

    // Its rail, [join] where it follows another term, the operand and its ratio in one outlined unit; the trash strip
    // deletes it, so it carries no x (decision term-operators-right-pack).
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawTerm(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        if (path.getLast() > 0 && piece.operand() instanceof ExpressionBuilder.Nest) {
            // Decision builder-inlines-terms-under-indentation-rules-with-a-colon: a parenthetical takes a line of
            // its own with its join, its controls too wide to share the rest of a line.
            flow.wrapLine();
        }
        int lead = leadWidth(flow.font(), path, piece);
        // Decision parentheticals-alternate-gray-tones-by-depth: the box's padding opens with its rail.
        flow.fit(BOX_INSIDE + ControlFlow.RAIL_ADVANCE + lead);
        BuilderOverlays.Anchor start = flow.anchor();
        flow.space(BOX_INSIDE);
        flow.rail(railHoverKey(path), new Drag(path, piece), lead);
        if (path.getLast() > 0) {
            drawJoin(flow, path, piece);
        }
        if (piece.operand() instanceof ExpressionBuilder.Nest nest) {
            drawNest(flow, new Nested(path, piece, start), nest, place);
        } else {
            drawOperand(flow, path, piece, place);
            if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
                drawFactor(flow, piece, (op, factor) -> apply(b -> b.setScale(path, op, factor)));
            }
            flow.space(BOX_INSIDE);
            flow.outlineFrom(start);
            boxes.add(new TermBox(path, flow.boxFrom(start, depthOf(path, place))));
        }
        flow.space(BOX_AFTER);
    }

    // The join and the operand after a term's rail, which the rail keeps on its line.
    private int leadWidth(Font font, List<Integer> path, ExpressionBuilder.Piece piece) {
        int join = path.getLast() > 0 ? ButtonPainter.width(font, PLUS_SIGN) + BuilderOverlays.GAP : 0;
        int operand = switch (piece.operand()) {
            case ExpressionBuilder.Num num -> ButtonPainter.width(font, num.digits());
            case ExpressionBuilder.Ref ref -> ExpressionLines.width(font, refRuns(ref)) + ButtonPainter.PADDING
                    + ButtonPainter.PADDING;
            case ExpressionBuilder.Nest _ -> font.width(OPEN_PAREN);
            case ExpressionBuilder.Kept _ -> ExpressionLines.width(font, operandRuns(piece));
        };
        return join + operand;
    }

    private static int depthOf(List<Integer> path, Place place) {
        return place.inRow() ? path.size() - 1 : path.size();
    }

    /**
     * Where a term drew, which the () handle wraps when dropped inside it.
     *
     * @param path the term's place
     * @param box  the box it spans
     */
    private record TermBox(List<Integer> path, GroupsPane.Bounds box) {
        boolean holds(double x, double y) {
            return x >= box.left() && x < box.right() && y >= box.top() && y < box.bottom();
        }

        long area() {
            return (long) (box.right() - box.left()) * (box.bottom() - box.top());
        }
    }

    // The innermost term box the last frame laid under the pointer, null where none holds it.
    private @Nullable TermBox boxUnder(double x, double y) {
        return boxes.stream().filter(term -> term.holds(x, y)).min(java.util.Comparator.comparingLong(TermBox::area))
                .orElse(null);
    }

    // The () handle beside [+?], or below a whole value's terms; dragged onto a term it wraps that term.
    // Decision parenthetical-handle-drags-onto-a-term.
    private void drawHandle(ControlFlow flow) {
        flow.grabControl(WRAP, new Wrapping());
    }

    /**
     * The () handle held: where the pointer stands and the term it would wrap.
     */
    private final class Wrapping implements ClickTargets.PointerGrab {
        private double x;
        private double y;
        private @Nullable TermBox target;

        @Override
        public void moved(double movedX, double movedY) {
            wrapping = this;
            x = movedX;
            y = movedY;
            target = boxUnder(x, y);
        }

        @Override
        public void released() {
            TermBox dropped = target;
            boolean held = wrapping == this;
            wrapping = null;
            if (held && dropped != null) {
                apply(b -> b.wrap(dropped.path()));
            }
        }
    }

    // The term the held () would wrap, filled in the cursor's blue.
    // Decision parenthetical-handle-drags-onto-a-term.
    private void drawWrapMark(GuiGraphicsExtractor graphics) {
        Wrapping held = wrapping;
        if (held == null || held.target == null) {
            return;
        }
        GroupsPane.Bounds box = held.target.box();
        graphics.fill(box.left(), box.top(), box.right(), box.bottom(), WRAP_MARK);
    }

    // The held () drawn at the pointer, over everything else.
    private void drawHeldHandle(GuiGraphicsExtractor graphics, Font font) {
        Wrapping held = wrapping;
        if (held == null) {
            return;
        }
        int left = (int) held.x + PREVIEW_OFFSET;
        int top = (int) held.y;
        int width = font.width(WRAP) + ButtonPainter.PADDING + ButtonPainter.PADDING;
        graphics.fill(left, top, left + width, top + ButtonPainter.HEIGHT, PREVIEW_COLOR);
        graphics.text(font, WRAP, left + ButtonPainter.PADDING, top + ButtonPainter.TEXT_NUDGE, PREVIEW_TEXT);
    }

    private void drawJoin(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece) {
        boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
        flow.control(plus ? PLUS_SIGN : MINUS_SIGN, null, at -> apply(b -> b.setJoin(path,
                plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
    }

    // A number as its box, an item or prefab as the button that replaces it, then 's and its goo type where it
    // takes one; a term no control builds draws as it reads.
    private void drawOperand(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        switch (piece.operand()) {
            case ExpressionBuilder.Num num -> drawNumber(flow, path, num, place);
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), null, at -> overlays.openDropdown(at, termList(place),
                        choice -> apply(b -> b.setOperand(path, refFor(choice, place)))));
                drawMember(flow, path, ref, place);
            }
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ -> flow.runs(operandRuns(piece), null,
                    null);
        }
    }

    // A click on the number opens what its amount comes from: number opens the number box, prefab a number prefab
    // whose quantity takes its place, relative a row's item or per-type prefab whose goo type does; a place reading
    // numbers alone opens the number box straight away.
    // Decision amount-modes-name-what-the-amount-comes-from.
    // Decision term-number-opens-number-prefab-relative-modes.
    private void drawNumber(ControlFlow flow, List<Integer> path, ExpressionBuilder.Num num, Place place) {
        Consumer<BuilderOverlays.Anchor> openBox = at -> overlays.openNumber(at, num.digits(),
                digits -> apply(b -> b.setNumber(path, digits)));
        List<BuilderOverlays.Option> modes = amountModes(path, place, openBox);
        flow.control(num.digits(), null, modes.size() > 1 ? at -> overlays.openOptions(at, modes) : openBox);
    }

    private List<BuilderOverlays.Option> amountModes(List<Integer> path, Place place,
                                                     Consumer<BuilderOverlays.Anchor> openBox) {
        List<BuilderOverlays.Option> modes = new ArrayList<>();
        modes.add(new BuilderOverlays.Option(NUMBER_MODE, null, openBox));
        List<String> scalars = grammar.scalarPrefabs();
        if (!scalars.isEmpty()) {
            modes.add(new BuilderOverlays.Option(PREFAB_MODE, null, at -> overlays.openDropdown(at,
                    SearchDropdown.over(List.of(), scalars, false), choice -> apply(b -> b.setOperand(path,
                            new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, choice.id(), null))))));
        }
        if (place.inRow() && (grammar.readsItems() || !grammar.perTypePrefabs().isEmpty())) {
            boolean items = grammar.readsItems();
            modes.add(new BuilderOverlays.Option(RELATIVE_MODE, null, at -> overlays.openDropdown(at,
                    SearchDropdown.over(items ? inventory.get() : List.of(), grammar.perTypePrefabs(), items),
                    choice -> apply(b -> b.setOperand(path, new ExpressionBuilder.Ref(choice.kind(), choice.id(),
                            place.rowType()))))));
        }
        return modes;
    }

    // A whole value's term may take its whole value again; a row's amount always reads one goo type.
    private void drawMember(ControlFlow flow, List<Integer> path, ExpressionBuilder.Ref ref, Place place) {
        String member = ref.member();
        if (member == null) {
            return;
        }
        flow.text(POSSESSIVE, GRAY);
        flow.typeIcon(member, at -> overlays.openPicker(at, type -> apply(b -> b.setMember(path, type)),
                place.inRow() ? null : () -> apply(b -> b.setMember(path, null))));
    }

    // "(" after the term's join, its terms inline, each edited in place, a wrap inside it one indent further in,
    // then ")" and the parenthetical's factor back at the outer indent; the box from its rail tints by depth.
    // Decision sum-of-terms-leaves-the-builder.
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    private void drawNest(ControlFlow flow, Nested nested, ExpressionBuilder.Nest nest, Place place) {
        List<Integer> path = nested.path();
        flow.text(OPEN_PAREN, GRAY);
        flow.shift(ControlFlow.INDENT);
        drawSum(flow, path, nest.pieces(), place);
        flow.shift(-ControlFlow.INDENT);
        flow.text(CLOSE_PAREN, GRAY);
        drawFactor(flow, nested.piece(), (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        int depth = depthOf(path, place);
        flow.space(BOX_INSIDE);
        flow.tintFrom(nested.start(), new ControlFlow.BoxTones(nestTone(depth), NEST_BORDER), depth);
        boxes.add(new TermBox(path, flow.boxFrom(nested.start(), depth)));
    }

    /**
     * A parenthetical as it draws: its place, its term and where its rail stood.
     *
     * @param path  its place
     * @param piece its term, which carries its factor
     * @param start where its rail stood, its box's top left
     */
    private record Nested(List<Integer> path, ExpressionBuilder.Piece piece, BuilderOverlays.Anchor start) {
    }

    /**
     * One side of a ratio set to a number.
     */
    @FunctionalInterface
    private interface ScaleChange {
        void changed(ExpressionBuilder.Op side, String factor);
    }

    // An unscaled number shows no factor, and a reference's or a sum's factor reads as a ratio, multiplier:divisor,
    // each a number box.
    // Decision factor-reads-as-a-ratio.
    // Decision scale-shows-the-ratio-at-once: an unscaled reference or sum draws 1:1 at rest, either side set first.
    // Decision ratio-holds-multiplier-and-divisor-together: each box sets its own side alone, so 2:3 draws 2 : 3.
    private void drawFactor(ControlFlow flow, ExpressionBuilder.Piece piece, ScaleChange change) {
        if (BuilderGrammar.factorOf(piece) != BuilderGrammar.Factor.HIDDEN) {
            drawRatio(flow, piece.multiplier(), piece.divisor(), change);
        }
    }

    private void drawRatio(ControlFlow flow, String multiplier, String divisor, ScaleChange change) {
        ratioSide(flow, multiplier, null, ExpressionBuilder.Op.MULTIPLY, change);
        flow.text(RATIO, GRAY);
        ratioSide(flow, divisor, null, ExpressionBuilder.Op.DIVIDE, change);
    }

    private void ratioSide(ControlFlow flow, String shown, String hint, ExpressionBuilder.Op side,
                           ScaleChange change) {
        // A side committed unchanged changes nothing.
        flow.control(shown, hint, at -> overlays.openNumber(at, shown, factor -> {
            if (!factor.equals(shown)) {
                change.changed(side, factor);
            }
        }, true));
    }

    private SearchDropdown termList(String sort) {
        boolean items = grammar.termTakesItems(sort);
        return SearchDropdown.over(items ? inventory.get() : List.of(), grammar.termPrefabs(sort), items);
    }

    // A row's amount reads numbers, number prefabs, and an item's or per-type prefab's goo type; a whole value reads
    // what its sort reads.
    private SearchDropdown termList(Place place) {
        if (!place.inRow()) {
            return termList(place.sort());
        }
        List<String> readable = new ArrayList<>(grammar.scalarPrefabs());
        readable.addAll(grammar.perTypePrefabs());
        boolean items = grammar.readsItems();
        return SearchDropdown.over(items ? inventory.get() : List.of(), readable, items);
    }

    private boolean hasTermChoices(Place place) {
        if (!place.inRow()) {
            return grammar.termTakesItems(place.sort()) || !grammar.termPrefabs(place.sort()).isEmpty();
        }
        return grammar.readsItems() || !grammar.scalarPrefabs().isEmpty() || !grammar.perTypePrefabs().isEmpty();
    }

    private boolean takesNumbers(Place place) {
        return place.inRow() || grammar.termTakesNumbers(place.sort());
    }

    // A row's term takes the row's own goo type from an item or a per-type prefab.
    private ExpressionBuilder.Ref refFor(SearchDropdown.Choice choice, Place place) {
        boolean typed = place.inRow() && (choice.kind() == SearchDropdown.Kind.ITEM
                || grammar.perTypePrefabs().contains(choice.id()));
        return new ExpressionBuilder.Ref(choice.kind(), choice.id(), typed ? place.rowType() : null);
    }

    private static ExpressionBuilder.Ref refOf(SearchDropdown.Choice choice) {
        return new ExpressionBuilder.Ref(choice.kind(), choice.id(), null);
    }

    private static List<Integer> placeOf(List<Integer> sum, int index) {
        List<Integer> path = new ArrayList<>(sum);
        path.add(index);
        return List.copyOf(path);
    }

    // The item or prefab alone, its goo type drawn by its own control.
    private List<ExpressionLines.Run> refRuns(ExpressionBuilder.Ref ref) {
        return operandRuns(ExpressionBuilder.firstTerm(new ExpressionBuilder.Ref(ref.kind(), ref.id(), null)));
    }

    private List<ExpressionLines.Run> operandRuns(ExpressionBuilder.Piece piece) {
        return ExpressionLines.runs(new ExpressionTerms.Expression(ExpressionBuilder.operandSum(piece)),
                icons::nameOf);
    }

    private void apply(Consumer<ExpressionBuilder> edit) {
        ExpressionBuilder edited = builder;
        if (edited == null) {
            return;
        }
        Optional<String> before = edited.written();
        edit.accept(edited);
        Optional<String> after = edited.written();
        if (writes != null && after.isPresent() && !after.equals(before)) {
            writes.accept(after.get());
        }
    }

    // Writes back the value the builder opened over, then closes it as done does.
    private void cancel() {
        overlays.discard();
        if (writes != null) {
            writes.accept(openedShown);
        }
        finish();
    }

    private void finish() {
        Runnable finished = done;
        close();
        if (finished != null) {
            finished.run();
        }
    }
}

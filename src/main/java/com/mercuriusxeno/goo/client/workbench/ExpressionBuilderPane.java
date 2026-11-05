package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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
    /** The room between sibling term containers. */
    static final int TERM_MARGIN = 8;
    // How far below a line's top its centered buttons start.
    private static final int BUTTON_ABOVE = (ControlFlow.LINE - ButtonPainter.HEIGHT) / 2;
    /** How far the builder's panel reaches above the top it is drawn at, the box margin outside its first buttons. */
    static final int PANEL_ABOVE = RowLayout.BOX_MARGIN - BUTTON_ABOVE;
    // Decision terms-and-type-rows-drag-to-a-trash-strip: the strip along the builder's right edge, dim at rest,
    // lit while a drag is in flight and brighter while the dragged thing hovers it.
    /** The trash strip's width. */
    static final int TRASH_WIDTH = 12;
    private static final int CAN_WIDTH = 8;
    private static final int CAN_HEIGHT = 10;
    private static final int CAN_BODY_TOP = 3;
    private static final int CAN_HANDLE_INSET = 3;
    private static final int CAN_RIB_INSET = 2;
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
    // Decision x-and-slash-drag-from-the-bay-onto-a-term.
    private static final String TIMES_SIGN = "x";
    private static final String DIVIDE_SIGN = "/";
    private static final String POSSESSIVE = "'s";
    private static final String WRAP = "()";
    private static final String TRASH_HINT = "Drag items here to remove";
    // Decision parenthetical-handle-carries-a-rail-and-hovers-its-drop: the handle reads how it is used.
    private static final String WRAP_HINT = "Drop on term, wrap w/ parenthesis";
    private static final String RAIL_NAME_PREFIX = "goo-term-rail:";
    private static final String ROW_RAIL_NAME_PREFIX = "goo-row-rail:";
    private static final int HALF = 2;
    // A pixel off the slot's line counts this many off along it, so the cursor keeps to the line the pointer is on.
    private static final int LINE_WEIGHT = 4;
    // The drop cursor shows for this many frames, then hides for as many.
    private static final int BLINK_FRAMES = 20;
    private static final int PREVIEW_OFFSET = 8;
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String ADD_TYPE = "+?";
    // Decision plus-question-and-ratio-boxes-hover-their-names: [+?] and the ratio's two boxes name themselves.
    private static final String ADD_TYPE_HINT = "Add a goo type";
    private static final String MULTIPLIER_HINT = "Multiplier";
    private static final String DIVISOR_HINT = "Divisor";
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
    // The + or - the bay's buttons hold while it drags.
    private @Nullable SignDrag signDragging;
    // The x or / the bay's buttons hold while it drags, and the side a drop opened at 1, whose segment draws for as
    // long as its number box stands open.
    private @Nullable FactorDrag factorDragging;
    private @Nullable PendingFactor pendingFactor;
    // Where the last frame's lines ended, short of the trash strip.
    private int linesRight;
    // What each rail laid this frame drags, by the name it drags: a term's path or a type row's index.
    private final java.util.Map<String, List<Integer>> termRails = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> rowRails = new java.util.HashMap<>();
    // Where the builder drew visible this frame, the pane's scissor, null where none clipped it.
    private DragMask.@Nullable Rect visible;
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
        factorDragging = null;
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
        // Decision x-and-slash-drag-from-the-bay-onto-a-term: Escape drops the held x or / and scales nothing.
        if (key == GLFW.GLFW_KEY_ESCAPE && holdsADrop()) {
            dragging = null;
            wrapping = null;
            rowDragging = null;
            factorDragging = null;
            return true;
        }
        return overlays.keyPressed(key);
    }

    // Whether a term, the () handle, a type row or the bay's x or / stands held, which Escape drops.
    private boolean holdsADrop() {
        boolean termOrHandle = dragging != null || wrapping != null;
        return termOrHandle || rowDragging != null || factorDragging != null;
    }

    /**
     * @param path a term's place
     * @return the name its drag rail grabs under, which hovering never reads
     */
    static String railName(List<Integer> path) {
        return RAIL_NAME_PREFIX + path;
    }

    /**
     * @param row a type row's index
     * @return the name its drag rail grabs under, which hovering never reads
     */
    static String rowRailName(int row) {
        return ROW_RAIL_NAME_PREFIX + row;
    }

    /**
     * @param depth how many parentheticals hold the box, its own included, from 1
     * @return the tint its box fills in
     */
    static int nestTone(int depth) {
        // Decision parentheticals-alternate-gray-tones-by-depth: an odd depth darkens its parent's tone a step and an
        // even one lightens it back, so each box reads off the one around it.
        return depth % HALF == 0 ? Palette.NEST_LIGHTER : Palette.NEST_DARKER;
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
        if (!overlays.numberOpen()) {
            pendingFactor = null;
        }
        startFrame();
        visible = visibleArea(graphics);
        int stripLeft = area.right() - TRASH_WIDTH;
        // Decision builder-font-and-spacing-shrink-padding-untouched: the builder letters its words smaller.
        ControlFlow flow = ControlFlow.lettered(graphics, font, targets, icons,
                new ControlFlow.Span(area.left(), stripLeft - BuilderOverlays.GAP, top), actions::openVariable);
        drawKind(flow, edited);
        drawValue(flow, edited);
        drawClosing(flow, edited);
        // A rail whose thing has nowhere to drop draws dark gray and takes no press, the operator's ruling of
        // 2026-11-03: no rail offers a drag that does nothing.
        flow.paintRails(this::railDraggable);
        int bottom = flow.bottom();
        drawPanel(graphics, new GroupsPane.Bounds(area.left(), top, area.right(), bottom));
        hoverTrash(targets);
        drawDropCursor(graphics);
        linesRight = stripLeft - BuilderOverlays.GAP;
        drawRowCursor(graphics, linesRight);
        drawWrapMark(graphics);
        return bottom - top;
    }

    // A new frame lays its slots, boxes and row slots afresh.
    private void startFrame() {
        frames++;
        slots.clear();
        boxes.clear();
        rowSlots.clear();
        termRails.clear();
        rowRails.clear();
    }

    // Whether the thing a rail drags, by the name it drags, has somewhere to drop this frame: a slot the builder's dry
    // runs take, or the trash strip, the same places its drag mask would light.
    private boolean railDraggable(String name) {
        List<Integer> term = termRails.get(name);
        if (term != null) {
            return holds(edited -> edited.canRemove(term)) || slots.stream().anyMatch(slot -> holds(edited ->
                    edited.canMoveTo(term, slot.sum(), slot.index())));
        }
        Integer row = rowRails.get(name);
        return row != null && (holds(edited -> edited.canRemoveRow(row)) || rowSlots.stream().anyMatch(slot ->
                holds(edited -> edited.canMoveRow(row, slot.index()))));
    }

    // The gray box behind the builder and the trash strip along its right edge, over the lines it laid.
    // Decision spacing-rules-are-one-set-across-every-pane: the box stands the box margin outside its buttons, which
    // a line centers below its top.
    private void drawPanel(GuiGraphicsExtractor graphics, GroupsPane.Bounds laid) {
        int panelTop = laid.top() - PANEL_ABOVE;
        graphics.fill(laid.left() - RowLayout.BOX_MARGIN, panelTop, laid.right(), laid.bottom(), Palette.PANEL_FILL);
        trash = new GroupsPane.Bounds(laid.right() - TRASH_WIDTH, panelTop, laid.right(), laid.bottom());
        drawTrash(graphics, trash);
    }

    // Decision trash-strip-hovers-drag-items-here-to-remove.
    private void hoverTrash(ClickTargets targets) {
        GroupsPane.Bounds strip = trash;
        if (strip != null) {
            targets.addHover(strip.left(), strip.top(), strip.right() - strip.left(), strip.bottom() - strip.top(),
                    BuilderOverlays.hintKey(TRASH_HINT));
        }
    }

    // Decision terms-and-type-rows-drag-to-a-trash-strip.
    private void drawTrash(GuiGraphicsExtractor graphics, GroupsPane.Bounds strip) {
        int tone = dragOverTrash() ? Palette.TRASH_HOVER : Palette.TRASH_REST;
        if (tone == Palette.TRASH_REST && dragDeletable()) {
            tone = Palette.TRASH_LIT;
        }
        graphics.fill(strip.left(), strip.top(), strip.right(), strip.bottom(), tone);
        // Decision trash-strip-draws-a-can-and-a-lighter-red-border.
        graphics.fill(strip.left(), strip.top(), strip.right(), strip.top() + 1, Palette.TRASH_BORDER);
        graphics.fill(strip.left(), strip.bottom() - 1, strip.right(), strip.bottom(), Palette.TRASH_BORDER);
        graphics.fill(strip.left(), strip.top() + 1, strip.left() + 1, strip.bottom() - 1, Palette.TRASH_BORDER);
        graphics.fill(strip.right() - 1, strip.top() + 1, strip.right(), strip.bottom() - 1, Palette.TRASH_BORDER);
        drawCan(graphics, strip.left() + (strip.right() - strip.left() - CAN_WIDTH) / HALF,
                strip.top() + (strip.bottom() - strip.top() - CAN_HEIGHT) / HALF);
    }

    // A trash can CAN_WIDTH wide from its body's left: a handle, a lid a pixel wider each side, and a body of two
    // sides, a bottom and two ribs.
    private static void drawCan(GuiGraphicsExtractor graphics, int left, int top) {
        int right = left + CAN_WIDTH;
        int bodyTop = top + CAN_BODY_TOP;
        int bottom = top + CAN_HEIGHT;
        graphics.fill(left + CAN_HANDLE_INSET, top, right - CAN_HANDLE_INSET, top + 1, Palette.TRASH_CAN);
        int lid = top + 1;
        graphics.fill(left - 1, lid, right + 1, lid + 1, Palette.TRASH_CAN);
        graphics.fill(left, bodyTop, left + 1, bottom, Palette.TRASH_CAN);
        graphics.fill(right - 1, bodyTop, right, bottom, Palette.TRASH_CAN);
        graphics.fill(left + 1, bottom - 1, right - 1, bottom, Palette.TRASH_CAN);
        int ribBottom = bottom - CAN_RIB_INSET;
        graphics.fill(left + CAN_RIB_INSET, bodyTop + 1, left + CAN_RIB_INSET + 1, ribBottom, Palette.TRASH_CAN);
        graphics.fill(right - CAN_RIB_INSET - 1, bodyTop + 1, right - CAN_RIB_INSET, ribBottom, Palette.TRASH_CAN);
    }

    // Decision only-term-or-row-drags-to-the-trash: the strip lights for any term or row held, the only one included.
    private boolean dragDeletable() {
        Drag term = dragging;
        RowDrag row = rowDragging;
        return term != null && term.deletable || row != null && row.deletable;
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
        graphics.fill(at.x(), at.y() - 1, right, at.y() + 1, Palette.DROP_CURSOR);
    }

    // Whether the open builder answers yes, false where none stands open.
    private boolean holds(java.util.function.Predicate<ExpressionBuilder> query) {
        ExpressionBuilder edited = builder;
        return edited != null && query.test(edited);
    }

    // Whether the pointer stands over the trash strip the last frame drew.
    private boolean overTrash(double x, double y) {
        GroupsPane.Bounds strip = trash;
        if (strip == null) {
            return false;
        }
        return x >= strip.left() && x < strip.right() && y >= strip.top() && y < strip.bottom();
    }

    // A blinking blue bar at the slot the held term, or the held + or -, would drop into.
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawDropCursor(GuiGraphicsExtractor graphics) {
        Slot target = heldSlot();
        if (target == null || frames / BLINK_FRAMES % HALF != 0) {
            return;
        }
        BuilderOverlays.Anchor at = target.at();
        int x = at.x() - BuilderOverlays.GAP / HALF;
        int[] span = spanOf(target);
        graphics.fill(x - 1, span[0], x + 1, span[1], Palette.DROP_CURSOR);
    }

    // The top and bottom a slot's cursor takes and its hole derives from: the interior of the layer the slot joins,
    // the segments' height of the container the slot stands beside; a slot beside no container, as an empty sum's,
    // takes its line.
    // Decision drop-indicators-size-to-the-container-they-land-beside.
    // Decision mask-holes-fit-the-indicator-and-the-layer-interior.
    private int[] spanOf(Slot slot) {
        List<Integer> beside = placeOf(slot.sum(), slot.beside());
        GroupsPane.Bounds box = boxes.stream().filter(drawn -> drawn.path().equals(beside)).map(TermBox::box)
                .findFirst().orElse(null);
        int top = box == null ? slot.at().y() : box.top();
        int bottom = box == null ? slot.at().y() + ControlFlow.LINE : box.bottom();
        return new int[] {top, bottom};
    }

    // Decision drags-mask-everything-but-their-valid-targets: while a term, a type row, the () handle or a + or -
    // is held, the screen darkens but over each place the builder's dry runs say it may drop.
    // Each place lights only where the builder drew visible, so a place scrolled under the pane's edge, as under the
    // search bar, lights its visible part or nothing.
    private void drawDragMask(GuiGraphicsExtractor graphics) {
        List<DragMask.Rect> holes = dropTargets();
        if (holes != null) {
            DragMask.Rect shown = visible;
            DragMask.draw(graphics, new DragMask.Rect(0, 0, graphics.guiWidth(), graphics.guiHeight()),
                    shown == null ? holes : holes.stream().map(hole -> hole.within(shown))
                            .filter(hole -> !hole.isEmpty()).toList());
        }
    }

    // The scissor the pane drew the builder under, null where none clipped it.
    private static DragMask.@Nullable Rect visibleArea(GuiGraphicsExtractor graphics) {
        ScreenRectangle scissor = graphics.peekScissorStack();
        return scissor == null ? null
                : new DragMask.Rect(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
    }

    // The held drag's targets, null where nothing is held.
    private @Nullable List<DragMask.Rect> dropTargets() {
        Drag term = dragging;
        RowDrag row = rowDragging;
        SignDrag sign = signDragging;
        if (term != null) {
            return withTrash(slotHoles(slot -> holds(edited -> edited.canMoveTo(term.from, slot.sum(),
                    slot.index()))), holds(edited -> edited.canRemove(term.from)));
        }
        if (row != null) {
            List<DragMask.Rect> holes = rowSlots.stream().filter(slot -> holds(edited -> edited.canMoveRow(row.row,
                    slot.index()))).map(this::rowSlotHole).toList();
            return withTrash(holes, holds(edited -> edited.canRemoveRow(row.row)));
        }
        if (sign != null) {
            return slotHoles(slot -> takesInsert(slot, sign.join));
        }
        if (factorDragging != null) {
            return boxes.stream().filter(TermBox::takesFactor).map(TermBox::rect).toList();
        }
        return wrapping == null ? null : boxes.stream().map(TermBox::rect).toList();
    }

    private List<DragMask.Rect> slotHoles(java.util.function.Predicate<Slot> takes) {
        return slots.stream().filter(takes).map(this::slotHole).toList();
    }

    // A slot's hole: the drop cursor's bar grown one pixel each way, centered on it, whether or not the blinking bar
    // draws this frame.
    // Decision mask-holes-fit-the-indicator-and-the-layer-interior.
    private DragMask.Rect slotHole(Slot slot) {
        int x = slot.at().x() - BuilderOverlays.GAP / HALF;
        int[] span = spanOf(slot);
        return aPixelAround(new DragMask.Rect(x - 1, span[0], x + 1, span[1]));
    }

    // A row slot's hole: the row cursor's bar, out to the lines' right end, grown one pixel each way.
    private DragMask.Rect rowSlotHole(RowSlot slot) {
        return aPixelAround(new DragMask.Rect(slot.at().x(), slot.at().y() - 1, linesRight, slot.at().y() + 1));
    }

    private static DragMask.Rect aPixelAround(DragMask.Rect bar) {
        return new DragMask.Rect(bar.left() - 1, bar.top() - 1, bar.right() + 1, bar.bottom() + 1);
    }

    private List<DragMask.Rect> withTrash(List<DragMask.Rect> holes, boolean removable) {
        GroupsPane.Bounds strip = trash;
        if (!removable || strip == null) {
            return holes;
        }
        List<DragMask.Rect> all = new ArrayList<>(holes);
        all.add(new DragMask.Rect(strip.left(), strip.top(), strip.right(), strip.bottom()));
        return all;
    }

    private @Nullable Slot heldSlot() {
        Drag term = dragging;
        if (term != null) {
            return term.target;
        }
        SignDrag sign = signDragging;
        return sign == null ? null : sign.target;
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
        // Decision one-lettering-primitive-names-three-text-roles: what a drag holds letters as the builder does.
        Lettering letters = Lettering.builder(preview.font());
        ClickTargets targets = preview.targets();
        int left = (int) pointerX + PREVIEW_OFFSET;
        int top = (int) pointerY;
        int width = ExpressionLines.width(letters, runs) + ButtonPainter.PADDING + ButtonPainter.PADDING;
        int height = ExpressionLines.lineHeight(runs);
        graphics.fill(left, top, left + width, top + height, Palette.DRAG_PREVIEW);
        ExpressionLines.draw(letters, runs, left + ButtonPainter.PADDING, top, width, new ExpressionLines.Painter(
                graphics, icons, targets, Palette.TEXT, Palette.LINK, (link, x, y, linkWidth) -> { }));
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
            drawDragMask(graphics);
            overlays.draw(graphics, font, targets);
            drawHeldTerm(graphics, font, targets);
            drawHeldRow(graphics, font, targets);
            drawHeldHandle(graphics, font);
            drawHeldSign(graphics, font);
            drawHeldFactor(graphics, font);
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
        int width = ButtonPainter.width(flow.letters(), CANCEL);
        if (changed) {
            width += BuilderOverlays.GAP + ButtonPainter.width(flow.letters(), DONE);
        }
        flow.alignRight(width);
        flow.coloredControl(CANCEL, Palette.DANGER, null, at -> cancel());
        if (changed) {
            flow.coloredControl(DONE, Palette.GOOD, null, at -> finish());
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
            flow.note(DENIED, Palette.MUTED);
        } else {
            flow.note(UNREAD, Palette.DANGER);
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
            flow.squareControl(ADD_TYPE, ADD_TYPE_HINT, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type)),
                    null, held));
        }
        drawHandle(flow);
        drawSignHandles(flow);
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

    // Each row one indent in: its goo type button, its rail the button's first segment, then its colon and its terms
    // inline, wrapping one indent further in, no box drawn around the row; a row slot stands before each row and
    // before [+?].
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    // Decision type-rows-drag-by-a-rail-to-reorder-or-trash: no x, the trash strip deleting the row.
    // Decision type-row-rail-sits-on-the-goo-type-button-with-no-box: the rail moves the row's terms with it.
    private void drawRows(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Row> rows = edited.rows();
        Set<String> held = BuilderGrammar.heldTypes(edited);
        for (int index = 0; index < rows.size(); index++) {
            ExpressionBuilder.Row row = rows.get(index);
            int at = index;
            flow.indent();
            rowSlots.add(new RowSlot(index, flow.anchor()));
            rowRails.put(rowRailName(index), index);
            flow.openSegments(ControlFlow.abutted(ControlFlow.RAIL_SEGMENT_WIDTH,
                    ButtonPainter.iconWidth(GooTypeIcons.sizeOf(row.type()))), ControlFlow.LINE);
            flow.railSegment(rowRailName(index), new RowDrag(index, row.type()));
            flow.typeSegment(row.type(), anchor -> overlays.openPicker(anchor,
                    type -> apply(b -> b.setRowType(at, type)), null, held));
            flow.closeSegments();
            flow.text(ExpressionLines.COLON, Palette.MUTED);
            drawSum(flow, List.of(index), row.pieces(), new Place(BuilderGrammar.NUMBER_SORT, row.type()));
            flow.outdent(ControlFlow.INDENT);
            // Decision type-rows-stand-apart-in-the-builder: the next row's buttons stand a button gap below.
            flow.gapBelow(RowLayout.BUTTON_GAP);
        }
        drawAddType(flow, edited);
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
        private boolean deletable;

        RowDrag(int row, String type) {
            this.row = row;
            this.type = type;
        }

        // Decision drags-indicate-only-a-drop-the-builder-takes: a row slot the builder takes; every row, the only one
        // included, lights the strip (decision only-term-or-row-drags-to-the-trash).
        @Override
        public void moved(double movedX, double movedY) {
            rowDragging = this;
            x = movedX;
            y = movedY;
            deletable = holds(edited -> edited.canRemoveRow(row));
            overTrash = deletable && overTrash(x, y);
            RowSlot nearest = overTrash(x, y) ? null : nearestRowSlot(y);
            target = nearest != null && holds(edited -> edited.canMoveRow(row, nearest.index())) ? nearest : null;
        }

        @Override
        public void released() {
            RowSlot dropped = target;
            boolean held = rowDragging == this;
            rowDragging = null;
            if (held && overTrash) {
                apply(b -> b.removeRow(row));
            } else if (held && dropped != null) {
                apply(b -> b.moveRow(row, dropped.index()));
            }
        }
    }

    // The whole value's terms on the line after the kind, one indent in.
    private void drawTerms(ControlFlow flow, ExpressionBuilder edited) {
        String sort = grammar.sortOf(edited);
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        flow.indent();
        // Decision one-x-is-implied-so-an-unscaled-term-draws-no-ratio: the sum draws no trailing ratio of its own; a
        // sum scales by wrapping it with () and dropping x or / on the parenthetical, as the file writes one.
        drawSum(flow, List.of(), pieces, new Place(sort, null));
        // Decision parenthetical-handle-drags-onto-a-term: the () handle below the terms, where [+?] stands for rows.
        flow.indent(0);
        drawHandle(flow);
        drawSignHandles(flow);
        flow.outdent(ControlFlow.INDENT);
    }

    // A sum's terms inline, each wrapping one indent in where the line runs out; a drop slot stands before each term
    // and after the last.
    // Decision terms-inline-again-under-the-indentation-rules.
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawSum(ControlFlow flow, List<Integer> sum, List<ExpressionBuilder.Piece> pieces, Place place) {
        for (int index = 0; index < pieces.size(); index++) {
            if (index > 0) {
                // The room between sibling containers stays, as the operator ruled when the containers were
                // segmented; a parenthetical's first and last terms stand a gap from its parens.
                flow.space(TERM_MARGIN - BuilderOverlays.GAP);
            }
            // A slot stands behind the term before it, on that term's last line, so its cursor spans that term's
            // container; the sum's first slot stands in front of its first term.
            slots.add(new Slot(sum, index, flow.anchor(), place, Math.max(0, index - 1)));
            drawTerm(flow, placeOf(sum, index), pieces.get(index), place);
        }
        slots.add(new Slot(sum, pieces.size(), flow.anchor(), place, pieces.size() - 1));
    }

    /**
     * Where a dragged term would drop.
     *
     * @param sum    the sum it drops into
     * @param index  the slot in it: before the term at that index, or the sum's size for its end
     * @param at     where the slot stands, the next piece's place on its line
     * @param place  where the sum stands, which names the terms a term inserted there takes
     * @param beside the index in the sum of the term or parenthetical the slot stands behind, or in front of for the
     *               sum's first slot, whose container its cursor and hole span (decision
     *               drop-indicators-size-to-the-container-they-land-beside)
     */
    private record Slot(List<Integer> sum, int index, BuilderOverlays.Anchor at, Place place, int beside) {
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
        private boolean deletable;

        Drag(List<Integer> from, ExpressionBuilder.Piece piece) {
            this.from = from;
            this.piece = piece;
        }

        // Decision drags-indicate-only-a-drop-the-builder-takes: the slot the term would drop into is one the builder
        // takes; every term, a sum's only one included, lights the strip (decision only-term-or-row-drags-to-the-trash).
        @Override
        public void moved(double movedX, double movedY) {
            dragging = this;
            x = movedX;
            y = movedY;
            deletable = holds(edited -> edited.canRemove(from));
            overTrash = deletable && overTrash(x, y);
            Slot nearest = overTrash(x, y) ? null : nearestSlot(x, y);
            target = nearest != null && holds(edited -> edited.canMoveTo(from, nearest.sum(), nearest.index()))
                    ? nearest : null;
        }

        // Decision terms-and-type-rows-drag-to-a-trash-strip: a release on the strip deletes the term.
        @Override
        public void released() {
            Slot dropped = target;
            boolean held = dragging == this;
            dragging = null;
            if (held && overTrash) {
                apply(b -> b.remove(from));
            } else if (held && dropped != null) {
                apply(b -> b.moveTo(from, dropped.sum(), dropped.index()));
            }
        }
    }

    // Decision plus-and-minus-are-draggable-buttons-in-the-control-bay: a + and a - in the bay beside the () handle,
    // each dragged to the slot where its term goes.
    // Decision x-and-slash-drag-from-the-bay-onto-a-term: an x and a / beside them, each dragged onto a term to give
    // it a multiplier or a divisor, hovering the names the factor's sides carry.
    private void drawSignHandles(ControlFlow flow) {
        flow.grabControl(PLUS_SIGN, new SignDrag(ExpressionTerms.Join.PLUS), null);
        flow.grabControl(MINUS_SIGN, new SignDrag(ExpressionTerms.Join.MINUS), null);
        flow.grabControl(TIMES_SIGN, new FactorDrag(ExpressionBuilder.Op.MULTIPLY), MULTIPLIER_HINT);
        flow.grabControl(DIVIDE_SIGN, new FactorDrag(ExpressionBuilder.Op.DIVIDE), DIVISOR_HINT);
    }

    /**
     * An x or / held from the bay: where the pointer stands and the term under it that takes a factor, a reference
     * or a parenthetical, never a bare number (decision x-and-slash-drag-from-the-bay-onto-a-term).
     */
    private final class FactorDrag implements ClickTargets.PointerGrab {
        private final ExpressionBuilder.Op side;
        private double x;
        private double y;
        private @Nullable TermBox target;

        FactorDrag(ExpressionBuilder.Op side) {
            this.side = side;
        }

        @Override
        public void moved(double movedX, double movedY) {
            factorDragging = this;
            x = movedX;
            y = movedY;
            target = boxes.stream().filter(TermBox::takesFactor).filter(term -> term.holds(x, y))
                    .min(java.util.Comparator.comparingLong(TermBox::area)).orElse(null);
        }

        // A release on a term opens the number box at that side of its factor, its value selected whole, a commit
        // scaling that side of that term alone; a release off every term scales nothing.
        @Override
        public void released() {
            TermBox dropped = target;
            boolean held = factorDragging == this;
            factorDragging = null;
            if (held && dropped != null && dropped.factors() != null) {
                Factors factors = dropped.factors();
                boolean multiplies = side == ExpressionBuilder.Op.MULTIPLY;
                String shown = multiplies ? factors.piece().multiplier() : factors.piece().divisor();
                List<Integer> path = dropped.path();
                BuilderOverlays.Anchor at = multiplies ? factors.multiplier() : factors.divisor();
                if (at == null) {
                    // The side stands at 1, its segment drawn next frame, which stands the box under it.
                    pendingFactor = new PendingFactor(path, side);
                    at = new BuilderOverlays.Anchor(dropped.box().left(), dropped.box().top());
                }
                openSide(shown, side, (op, factor) -> apply(b -> b.setScale(path, op, factor))).accept(at);
            }
        }
    }

    /**
     * A factor side a bay drop opened at 1: its segment reads 1x or /1 for as long as its number box stands open
     * (decisions x-and-slash-drag-from-the-bay-onto-a-term, multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after).
     *
     * @param path the term's place
     * @param side the side the drop opened
     */
    private record PendingFactor(List<Integer> path, ExpressionBuilder.Op side) {
    }

    // Whether a term draws a factor side's segment: a side other than 1, or the side a drop opened at 1 (decision
    // one-x-is-implied-so-an-unscaled-term-draws-no-ratio).
    private boolean showsSide(List<Integer> path, ExpressionBuilder.Piece piece, ExpressionBuilder.Op side) {
        boolean multiplies = side == ExpressionBuilder.Op.MULTIPLY;
        String value = multiplies ? piece.multiplier() : piece.divisor();
        PendingFactor pending = pendingFactor;
        boolean opened = pending != null && pending.path().equals(path) && pending.side() == side;
        return BuilderGrammar.takesFactor(piece) && (!ExpressionBuilder.ONE_TEXT.equals(value) || opened);
    }

    // A factor side's label: the multiplier as Nx, the divisor as /n (decision
    // multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after).
    private static String sideLabel(ExpressionBuilder.Piece piece, ExpressionBuilder.Op side) {
        return side == ExpressionBuilder.Op.MULTIPLY ? piece.multiplier() + TIMES_SIGN
                : DIVIDE_SIGN + piece.divisor();
    }

    // A factor side as its term's segment, a click opening its number box; the side a drop opened draws here too,
    // its box standing under it. Answers where it stands, null where it draws none.
    private BuilderOverlays.@Nullable Anchor drawSide(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece,
                                                      ExpressionBuilder.Op side) {
        if (!showsSide(path, piece, side)) {
            return null;
        }
        boolean multiplies = side == ExpressionBuilder.Op.MULTIPLY;
        String shown = multiplies ? piece.multiplier() : piece.divisor();
        BuilderOverlays.Anchor at = flow.labelSegment(sideLabel(piece, side), multiplies ? MULTIPLIER_HINT
                : DIVISOR_HINT, openSide(shown, side, (op, factor) -> apply(b -> b.setScale(path, op, factor))));
        PendingFactor pending = pendingFactor;
        if (pending != null && pending.path().equals(path) && pending.side() == side) {
            overlays.moveNumber(at);
        }
        return at;
    }

    // The width a factor side's segment takes where the term draws it, 0 where it draws none.
    private int sideWidth(Lettering letters, List<Integer> path, ExpressionBuilder.Piece piece,
                          ExpressionBuilder.Op side) {
        return showsSide(path, piece, side) ? ButtonPainter.width(letters, sideLabel(piece, side)) : 0;
    }

    // The held x or / drawn at the pointer, over everything else.
    private void drawHeldFactor(GuiGraphicsExtractor graphics, Font font) {
        FactorDrag held = factorDragging;
        if (held != null) {
            drawHeldLabel(graphics, font, held.side == ExpressionBuilder.Op.MULTIPLY ? TIMES_SIGN : DIVIDE_SIGN,
                    held.x, held.y);
        }
    }

    /**
     * A + or - held from the bay: where the pointer stands and the slot its term would drop into.
     */
    private final class SignDrag implements ClickTargets.PointerGrab {
        private final ExpressionTerms.Join join;
        private double x;
        private double y;
        private @Nullable Slot target;

        SignDrag(ExpressionTerms.Join join) {
            this.join = join;
        }

        // Decision drags-indicate-only-a-drop-the-builder-takes: the slot is one the builder inserts a term at.
        @Override
        public void moved(double movedX, double movedY) {
            signDragging = this;
            x = movedX;
            y = movedY;
            Slot nearest = overTrash(x, y) ? null : nearestSlot(x, y);
            target = nearest != null && takesInsert(nearest, join) ? nearest : null;
        }

        @Override
        public void released() {
            Slot dropped = target;
            boolean held = signDragging == this;
            signDragging = null;
            if (held && dropped != null) {
                insertTerm(dropped, join);
            }
        }
    }

    private boolean takesInsert(Slot slot, ExpressionTerms.Join join) {
        return (hasTermChoices(slot.place()) || takesNumbers(slot.place()))
                && holds(edited -> edited.canInsertAt(slot.sum(), slot.index(), join));
    }

    // The term dropped: an item or prefab picked from the list at the slot, or a 1 where the sum reads only numbers.
    private void insertTerm(Slot slot, ExpressionTerms.Join join) {
        Place place = slot.place();
        if (hasTermChoices(place)) {
            overlays.openDropdown(slot.at(), termList(place), choice -> apply(b -> b.insertAt(slot.sum(),
                    slot.index(), refFor(choice, place), join)));
        } else {
            apply(b -> b.insertAt(slot.sum(), slot.index(), new ExpressionBuilder.Num(ExpressionBuilder.ONE_TEXT),
                    join));
        }
    }

    // A plain term or a parenthetical, each one segmented container; the trash strip deletes it, so it carries no x
    // (decision term-operators-right-pack).
    // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
    private void drawTerm(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        if (!(piece.operand() instanceof ExpressionBuilder.Nest nest)) {
            drawPlainTerm(flow, path, piece, place);
            return;
        }
        if (path.getLast() > 0) {
            // Decision builder-inlines-terms-under-indentation-rules-with-a-colon: a parenthetical that does not fit
            // the rest of the line goes with its join onto a line of its own.
            flow.fit(termWidth(flow.letters(), path, piece));
        }
        drawNest(flow, path, piece, place);
    }

    // A term no parenthetical holds open, as one segmented container: its rail, its sign, its multiplier as Nx, its
    // operand with a member's 's, its goo type, then its divisor as /n, each a button editing what it names, a side at
    // 1 drawing none; the container wraps whole, so no term splits across lines.
    // Decision multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after.
    // Decision one-x-is-implied-so-an-unscaled-term-draws-no-ratio.
    // Decision containers-segment-into-click-to-edit-buttons.
    // Decision terms-inline-again-under-the-indentation-rules.
    private void drawPlainTerm(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        Lettering letters = flow.letters();
        // Decision term-draws-a-two-pixel-brighter-border-with-two-tone-seams: a term's container rings.
        flow.openTerm(plainTermWidth(letters, path, piece), segmentsHeightOf(piece));
        // The rail opens every term's container, its sign right after it, the first term's too (decision
        // every-term-carries-its-sign-as-a-segment, and the operator's ruling of 2026-11-04 placing the rail left of
        // the sign).
        termRails.put(railName(path), path);
        flow.railSegment(railName(path), new Drag(path, piece));
        drawJoinSegment(flow, path, piece);
        BuilderOverlays.Anchor multiplier = drawSide(flow, path, piece, ExpressionBuilder.Op.MULTIPLY);
        drawOperand(flow, path, piece, place);
        BuilderOverlays.Anchor divisor = drawSide(flow, path, piece, ExpressionBuilder.Op.DIVIDE);
        boxes.add(new TermBox(path, flow.closeSegments(), BuilderGrammar.takesFactor(piece)
                ? new Factors(piece, multiplier, divisor) : null));
    }

    /**
     * Where a term's factor sides stand, so a bay drop opens a side's number box where a click on it would.
     *
     * @param piece      the term
     * @param multiplier where its multiplier's box opens, null where the side stands at 1 and draws no segment
     * @param divisor    where its divisor's box opens, null where the side stands at 1 and draws no segment
     */
    private record Factors(ExpressionBuilder.Piece piece, BuilderOverlays.@Nullable Anchor multiplier,
                           BuilderOverlays.@Nullable Anchor divisor) {
    }

    // The runs a plain term's operand segment holds, which set the container's height.
    private List<ExpressionLines.Run> operandSegmentRuns(ExpressionBuilder.Piece piece) {
        return piece.operand() instanceof ExpressionBuilder.Ref ref ? refRuns(ref) : operandRuns(piece);
    }

    // The width a plain term's container takes: its rail, sign, Nx, name with its 's, a member's type, and /n.
    private int plainTermWidth(Lettering letters, List<Integer> path, ExpressionBuilder.Piece piece) {
        List<Integer> widths = new ArrayList<>();
        widths.add(ControlFlow.RAIL_SEGMENT_WIDTH);
        widths.add(ButtonPainter.width(letters, joinLabel(piece)));
        widths.add(sideWidth(letters, path, piece, ExpressionBuilder.Op.MULTIPLY));
        widths.add(switch (piece.operand()) {
            case ExpressionBuilder.Num num -> ButtonPainter.width(letters, numberLabel(path, num));
            case ExpressionBuilder.Ref ref -> ControlFlow.runsSegmentWidth(letters, refRuns(ref), possessiveOf(ref));
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ ->
                    ControlFlow.runsSegmentWidth(letters, operandRuns(piece), BLANK);
        });
        if (piece.operand() instanceof ExpressionBuilder.Ref ref && ref.member() != null) {
            widths.add(ControlFlow.typeSegmentWidth(ref.member()));
        }
        widths.add(sideWidth(letters, path, piece, ExpressionBuilder.Op.DIVIDE));
        return ControlFlow.ringedWidth(widths.stream().mapToInt(Integer::intValue).filter(width -> width > 0)
                .toArray());
    }

    // A reference's 's, drawn in its name's segment where it takes a member (the operator's ruling of 2026-11-04).
    private static String possessiveOf(ExpressionBuilder.Ref ref) {
        return ref.member() == null ? BLANK : POSSESSIVE;
    }

    // A parenthetical's lead run, its rail, sign and Nx abutted.
    private int leadWidth(Lettering letters, List<Integer> path, ExpressionBuilder.Piece piece) {
        int multiplier = sideWidth(letters, path, piece, ExpressionBuilder.Op.MULTIPLY);
        int lead = ControlFlow.abutted(ControlFlow.RAIL_SEGMENT_WIDTH, ButtonPainter.width(letters, joinLabel(piece)));
        return multiplier == 0 ? lead : ControlFlow.abutted(lead, multiplier);
    }

    // The width a term takes laid on one line and the room after it: a plain term's segments, or a parenthetical's
    // lead run, its container around its terms and its ratio.
    private int termWidth(Lettering letters, List<Integer> path, ExpressionBuilder.Piece piece) {
        if (!(piece.operand() instanceof ExpressionBuilder.Nest nest)) {
            return plainTermWidth(letters, path, piece) + TERM_MARGIN;
        }
        int width = leadWidth(letters, path, piece)
                + ControlFlow.parenFrameWidth(letters.font(), OPEN_PAREN, CLOSE_PAREN, contentHeight(nest))
                + BuilderOverlays.GAP;
        for (int index = 0; index < nest.pieces().size(); index++) {
            width += termWidth(letters, placeOf(path, index), nest.pieces().get(index));
        }
        return width + sideWidth(letters, path, piece, ExpressionBuilder.Op.DIVIDE) + TERM_MARGIN;
    }

    private static int depthOf(List<Integer> path, Place place) {
        return place.inRow() ? path.size() - 1 : path.size();
    }

    // The height of what a parenthetical's container holds: its tallest term's segments, a 16 px item sprite's line
    // where a term shows one, or an inner parenthetical's container.
    private int contentHeight(ExpressionBuilder.Nest nest) {
        return nest.pieces().stream().mapToInt(piece -> piece.operand() instanceof ExpressionBuilder.Nest inner
                ? ControlFlow.parenContainerHeight(contentHeight(inner))
                : ControlFlow.ringedHeight(segmentsHeightOf(piece))).max().orElse(ControlFlow.LINE);
    }

    // A plain term's segments' height: the builder's line, or a 16 px item sprite's taller line.
    private int segmentsHeightOf(ExpressionBuilder.Piece piece) {
        return operandSegmentRuns(piece).stream().anyMatch(run -> run.kind() == ExpressionLines.Kind.ITEM)
                ? ExpressionLines.ICON_LINE_HEIGHT : ControlFlow.LINE;
    }

    /**
     * Where a term drew, which the () handle wraps when dropped inside it.
     *
     * @param path    the term's place
     * @param box     the box it spans
     * @param factors where its factor's sides stand, null where the bay's x and / scale it not
     */
    private record TermBox(List<Integer> path, GroupsPane.Bounds box, @Nullable Factors factors) {
        // Decision x-and-slash-drag-from-the-bay-onto-a-term: a reference or a parenthetical takes the bay's x and /.
        boolean takesFactor() {
            return factors != null;
        }

        DragMask.Rect rect() {
            return new DragMask.Rect(box.left(), box.top(), box.right(), box.bottom());
        }

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
        flow.grabControl(WRAP, new Wrapping(), WRAP_HINT);
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
        graphics.fill(box.left(), box.top(), box.right(), box.bottom(), Palette.WRAP_MARK);
    }

    // The held () drawn at the pointer, over everything else.
    private void drawHeldHandle(GuiGraphicsExtractor graphics, Font font) {
        Wrapping held = wrapping;
        if (held != null) {
            drawHeldLabel(graphics, font, WRAP, held.x, held.y);
        }
    }

    // The held + or - drawn at the pointer, over everything else.
    private void drawHeldSign(GuiGraphicsExtractor graphics, Font font) {
        SignDrag held = signDragging;
        if (held != null) {
            drawHeldLabel(graphics, font, held.join == ExpressionTerms.Join.PLUS ? PLUS_SIGN : MINUS_SIGN, held.x,
                    held.y);
        }
    }

    private static void drawHeldLabel(GuiGraphicsExtractor graphics, Font font, String label, double pointerX,
                                      double pointerY) {
        Lettering letters = Lettering.builder(font);
        int left = (int) pointerX + PREVIEW_OFFSET;
        int top = (int) pointerY;
        int width = letters.width(label) + ButtonPainter.PADDING + ButtonPainter.PADDING;
        graphics.fill(left, top, left + width, top + ButtonPainter.HEIGHT, Palette.DRAG_PREVIEW);
        letters.draw(graphics, label, left + ButtonPainter.PADDING, letters.centeredTop(top, ButtonPainter.HEIGHT),
                Palette.TEXT);
    }

    // A first term added reads + as every added term does (decision every-term-carries-its-sign-as-a-segment).
    private static String joinLabel(ExpressionBuilder.Piece piece) {
        return piece.join() == ExpressionTerms.Join.MINUS ? MINUS_SIGN : PLUS_SIGN;
    }

    // A term's or a parenthetical's join, the segment its container opens on.
    private void drawJoinSegment(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece) {
        flow.labelSegment(joinLabel(piece), null, at -> flipJoin(path, piece));
    }

    private void flipJoin(List<Integer> path, ExpressionBuilder.Piece piece) {
        boolean minus = piece.join() == ExpressionTerms.Join.MINUS;
        apply(b -> b.setJoin(path, minus ? ExpressionTerms.Join.PLUS : ExpressionTerms.Join.MINUS));
    }

    // A number as its segment, an item or prefab as the segment that replaces it, its 's inside that segment, then
    // its goo type's segment where it takes one; a term no control builds draws as it reads, in a segment taking no
    // click.
    private void drawOperand(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        switch (piece.operand()) {
            case ExpressionBuilder.Num num -> drawNumber(flow, path, num, place);
            case ExpressionBuilder.Ref ref -> {
                flow.runsSegment(refRuns(ref), possessiveOf(ref), refPressed(path, place));
                drawMember(flow, path, ref, place);
            }
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ -> flow.runsSegment(operandRuns(piece), BLANK,
                    null);
        }
    }

    // A click on the number opens what its amount comes from: number opens the number box, prefab a number prefab
    // whose quantity takes its place, relative a row's item or per-type prefab whose goo type does; a place reading
    // numbers alone opens the number box straight away.
    // Decision amount-modes-name-what-the-amount-comes-from.
    // Decision term-number-opens-number-prefab-relative-modes.
    private void drawNumber(ControlFlow flow, List<Integer> path, ExpressionBuilder.Num num, Place place) {
        String shown = numberLabel(path, num);
        Consumer<BuilderOverlays.Anchor> openBox = at -> overlays.openMixedNumber(at, shown,
                digits -> apply(b -> b.setNumber(path, digits)));
        List<BuilderOverlays.Option> modes = amountModes(path, place, openBox);
        flow.labelSegment(shown, null, modes.size() > 1 ? at -> overlays.openOptions(at, modes) : openBox);
    }

    // A click on a prefab or item term opens the amount modes where its place reads numbers, number opening the box on
    // the term's count and standing the term on the number typed as its whole value, so a term set to a ref changes
    // back; elsewhere it opens the list of what may stand in its place.
    // Decision ref-term-takes-a-number-again; the operator's ruling of 2026-11-04 drops the count.
    // Decision term-number-opens-number-prefab-relative-modes.
    private Consumer<BuilderOverlays.Anchor> refPressed(List<Integer> path, Place place) {
        Consumer<BuilderOverlays.Anchor> pickRef = at -> overlays.openDropdown(at, termList(place),
                choice -> apply(b -> b.setOperand(path, refFor(choice, place))));
        if (!takesNumbers(place)) {
            return pickRef;
        }
        ExpressionBuilder edited = builder;
        String count = edited != null ? edited.countText(path) : ExpressionBuilder.ONE_TEXT;
        Consumer<BuilderOverlays.Anchor> openBox = at -> overlays.openMixedNumber(at, count,
                digits -> apply(b -> b.setNumber(path, digits)));
        List<BuilderOverlays.Option> modes = amountModes(path, place, openBox);
        return at -> overlays.openOptions(at, modes);
    }

    // A number term reads its number over its divisor as a mixed number, as 2 1/2, on its segment and in its box; a
    // number draws no divisor segment of its own, so the label carries it.
    // Decision number-box-takes-a-mixed-number.
    private String numberLabel(List<Integer> path, ExpressionBuilder.Num num) {
        ExpressionBuilder edited = builder;
        return edited != null ? edited.numberText(path) : num.digits();
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
        flow.typeSegment(member, at -> overlays.openPicker(at, type -> apply(b -> b.setMember(path, type)),
                place.inRow() ? null : () -> apply(b -> b.setMember(path, null))));
    }

    // A parenthetical: its rail, sign and Nx, then its tinted container with a 1 px border, a clear pixel between the
    // border and "(", "(" and its terms, its terms and ")", and ")" and the border, the parens regular weight and as
    // tall as the container's contents, so a nested one draws smaller; then its /n after the container, a side at 1
    // drawing none (decisions multiplier-reads-nx-before-the-identity-and-divisor-slash-n-after,
    // one-x-is-implied-so-an-unscaled-term-draws-no-ratio). Its terms
    // stand inline, each edited in place, a wrap inside it one indent further in, the indent returning at its exit.
    // The operator's rulings of 2026-11-04.
    // Decision sum-of-terms-leaves-the-builder.
    // Decision builder-inlines-terms-under-indentation-rules-with-a-colon.
    // Decision parentheticals-alternate-gray-tones-by-depth.
    private void drawNest(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, Place place) {
        ExpressionBuilder.Nest nest = (ExpressionBuilder.Nest) piece.operand();
        Lettering letters = flow.letters();
        int content = contentHeight(nest);
        int height = ControlFlow.parenContainerHeight(content);
        // The rail and sign touch the box's left border, so they wrap with its "(" (the operator's ruling of
        // 2026-11-04).
        flow.openSegments(leadWidth(letters, path, piece) + ControlFlow.parenOpeningWidth(letters.font(), OPEN_PAREN,
                content), height);
        termRails.put(railName(path), path);
        flow.railSegment(railName(path), new Drag(path, piece));
        drawJoinSegment(flow, path, piece);
        BuilderOverlays.Anchor multiplier = drawSide(flow, path, piece, ExpressionBuilder.Op.MULTIPLY);
        GroupsPane.Bounds lead = flow.closeSegments();
        flow.space(-BuilderOverlays.GAP);
        ControlFlow.OpenParenthetical opened = flow.openParenthetical(OPEN_PAREN, content, Palette.MUTED);
        drawSum(flow, path, nest.pieces(), place);
        GroupsPane.Bounds container = flow.closeParenthetical(opened, CLOSE_PAREN, Palette.MUTED);
        // Decision diagnose-and-correct-parenthetical-trash-and-out-drags: the slot after the parenthetical stands
        // right of its container too, so the pointer just past ")" drops outside it rather than at the inner sum's
        // end.
        slots.add(new Slot(path.subList(0, path.size() - 1), path.getLast() + 1, flow.anchor(), place,
                path.getLast()));
        int right = container.right();
        BuilderOverlays.Anchor divisor = null;
        int divisorWidth = sideWidth(letters, path, piece, ExpressionBuilder.Op.DIVIDE);
        if (divisorWidth > 0) {
            flow.openSegments(divisorWidth, height);
            divisor = drawSide(flow, path, piece, ExpressionBuilder.Op.DIVIDE);
            right = Math.max(right, flow.closeSegments().right());
        }
        flow.boxed(container, nestTone(depthOf(path, place)), Palette.NEST_BORDER);
        boxes.add(new TermBox(path, new GroupsPane.Bounds(lead.left(), Math.min(lead.top(), container.top()), right,
                Math.max(lead.bottom(), container.bottom())), new Factors(piece, multiplier, divisor)));
    }

    /**
     * One side of a ratio set to a number.
     */
    @FunctionalInterface
    private interface ScaleChange {
        void changed(ExpressionBuilder.Op side, String factor);
    }

    // A side committed unchanged changes nothing; its box takes a 0, which erases the side (decision
    // factor-left-at-one-or-set-to-one-or-zero-erases-its-segment).
    private Consumer<BuilderOverlays.Anchor> openSide(String shown, ExpressionBuilder.Op side, ScaleChange change) {
        return at -> overlays.openNumber(at, shown, factor -> {
            if (!factor.equals(shown)) {
                change.changed(side, factor);
            }
        });
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

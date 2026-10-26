package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Draws the expression builder over a value's curated view (decisions
 * expression-builder-composes-every-form-without-typing, goo-type-picker-is-a-seven-by-four-icon-grid). Its first
 * line chooses the value's kind: one scalar expression, or an amount per goo type. A scalar's terms each hold a
 * number, an item or a prefab, every one changed by a click on it, joined by plus or minus, scaled by a factor that
 * multiplies or divides, and put in parentheses with the next term; a per-goo-type value holds a row per type, its
 * icon changing the type and its amount a number, a scalar prefab or a goo type taken from an item. Every control
 * names what it does on hover, the lines wrap inside the pane, and [done] closes the builder below the value. Each
 * change writes the string the file grammar reads; a value is never written empty. The state lives in
 * {@link ExpressionBuilder}.
 */
final class ExpressionBuilderPane {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int PANEL_COLOR = 0x30FFFFFF;
    private static final String KIND = "Kind:";
    private static final String PER_TYPE = "per goo type";
    private static final String PER_TYPE_HINT = "A separate amount for each goo type";
    private static final String AMOUNT_FROM = "amount from";
    private static final String NUMBER_MODE = "number";
    private static final String NUMBER_MODE_HINT = "The amount is a number you set";
    private static final String PREFAB_MODE = "prefab";
    private static final String PREFAB_MODE_HINT = "The amount is a scalar prefab's value";
    private static final String ITEM_MODE = "item";
    private static final String ITEM_MODE_HINT = "The amount is one goo type of an item's or prefab's value";
    private static final String NUMBER_HINT = "Click to change the number";
    private static final String PREFAB_HINT = "Click to pick a different scalar prefab";
    private static final String REF_HINT = "Click to pick a different item or prefab";
    private static final String PLUS = "plus";
    private static final String MINUS = "minus";
    private static final String JOIN_HINT = "Click to switch between adding and subtracting this term";
    private static final String TIMES = "times";
    private static final String DIVIDED_BY = "divided by";
    private static final String OP_HINT = "Click to switch between multiplying and dividing";
    private static final String FACTOR_HINT = "Click to change the factor";
    private static final String ONE_TYPE = "[only one goo type]";
    private static final String ONE_TYPE_HINT = "Use only one goo type of this value";
    private static final String ONLY = "only";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String WRAP = "[( ) with next]";
    private static final String WRAP_HINT = "Put this term and the next inside parentheses, so one factor scales both";
    private static final String UNWRAP = "[remove ( )]";
    private static final String UNWRAP_HINT = "Take these terms out of their parentheses";
    private static final String KEPT_HINT = "No control builds this term; remove it to rebuild it";
    private static final String REMOVE = "[remove]";
    private static final String REMOVE_TERM_HINT = "Remove this term";
    private static final String REMOVE_ROW_HINT = "Remove this goo type's amount";
    private static final String ADD_TYPE = "[+ goo type]";
    private static final String ADD_TYPE_HINT = "Add an amount for another goo type";
    private static final String ADD_NUMBER = "[+ number]";
    private static final String ADD_NUMBER_HINT = "Add a number to the value";
    private static final String ADD_REF = "[+ item or prefab]";
    private static final String ADD_REF_HINT = "Add what an item or prefab is worth to the value";
    private static final String PICK_TYPE = "Pick the first goo type:";
    private static final String UNREAD = "This value cannot be read; pick a kind to start over.";
    private static final String EMPTY = "No value yet: pick a kind to give it one.";
    private static final String DENIED = "Denied: this holds no goo. Pick a kind to give it a value.";
    private static final String DONE = "[done]";
    private static final String DONE_HINT = "Close the builder; every change is already in the draft";

    private final ItemIcons icons;
    private final BuilderOverlays overlays;
    private final Supplier<List<ValuesTab.ManagerEntry>> inventory;
    private final Supplier<List<String>> prefabs;
    private final Supplier<List<String>> scalarPrefabs;
    private final Kinds kinds;
    private @Nullable ExpressionBuilder builder;
    private @Nullable Consumer<String> writes;
    private @Nullable Runnable done;

    /**
     * What a value's scalar kind is called where the builder edits it.
     *
     * @param scalar     the scalar kind's name
     * @param scalarHint what the scalar kind holds
     */
    record Kinds(String scalar, String scalarHint) {
    }

    /**
     * @param actions       the panes' line back to the screen
     * @param icons         the items' icons
     * @param inventory     the server's items, which item terms and amounts list
     * @param prefabs       every prefab's name
     * @param scalarPrefabs the scalar prefabs' names, which a prefab amount lists
     * @param kinds         what the scalar kind is called here
     */
    ExpressionBuilderPane(ManagerActions actions, ItemIcons icons, Supplier<List<ValuesTab.ManagerEntry>> inventory,
                          Supplier<List<String>> prefabs, Supplier<List<String>> scalarPrefabs, Kinds kinds) {
        this.icons = icons;
        this.overlays = new BuilderOverlays(actions, icons);
        this.inventory = inventory;
        this.prefabs = prefabs;
        this.scalarPrefabs = scalarPrefabs;
        this.kinds = kinds;
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        overlays.init(font);
    }

    /**
     * Opens the builder over a value.
     *
     * @param shown  the value as the draft's edit box shows it
     * @param change takes the string each change writes
     * @param finish runs when [done] closes the builder
     */
    void open(String shown, Consumer<String> change, Runnable finish) {
        overlays.close();
        builder = ExpressionBuilder.of(shown);
        writes = change;
        done = finish;
    }

    /**
     * Closes the builder, keeping what it wrote.
     */
    void close() {
        overlays.close();
        builder = null;
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
        return overlays.keyPressed(key);
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
        ControlFlow flow = new ControlFlow(graphics, font, targets, icons, area.left(), area.right(), top);
        drawKind(flow, edited);
        edited.refusal().ifPresent(reason -> flow.note(reason, RED));
        drawValue(flow, edited);
        flow.newLine();
        flow.control(DONE, DONE_HINT, at -> finish());
        int bottom = flow.bottom();
        graphics.fill(area.left() - 1, top - 1, area.right(), bottom, PANEL_COLOR);
        return bottom - top;
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
        }
    }

    /**
     * @return whether the builder's list or grid stands open, so nothing else draws over it
     */
    boolean listOpen() {
        return builder != null && overlays.listOpen();
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
            boolean empty = edited.text().isBlank();
            flow.note(empty ? EMPTY : UNREAD, empty ? GRAY : RED);
        }
    }

    private void drawKind(ControlFlow flow, ExpressionBuilder edited) {
        flow.text(KIND, GRAY);
        boolean perType = edited.isPerType();
        flow.choice(kinds.scalar(), kinds.scalarHint(), !perType && !edited.terms().isEmpty(),
                () -> apply(ExpressionBuilder::switchToWhole));
        flow.choice(PER_TYPE, PER_TYPE_HINT, perType, () -> apply(ExpressionBuilder::switchToPerType));
    }

    private void drawRows(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Row> rows = edited.rows();
        for (int index = 0; index < rows.size(); index++) {
            flow.newLine();
            drawRow(flow, index, rows.get(index), rows.size() > 1);
        }
        flow.newLine();
        if (rows.isEmpty()) {
            flow.text(PICK_TYPE, GRAY);
        }
        flow.control(ADD_TYPE, ADD_TYPE_HINT, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type))));
    }

    private void drawRow(ControlFlow flow, int row, ExpressionBuilder.Row amount, boolean removable) {
        flow.typeIcon(amount.type(), at -> overlays.openPicker(at, type -> apply(b -> b.setRowType(row, type))));
        ExpressionBuilder.Piece piece = amount.pieces().getFirst();
        if (amount.pieces().size() == 1 && !(piece.operand() instanceof ExpressionBuilder.Kept)) {
            drawAmount(flow, row, piece);
        } else {
            flow.runs(ExpressionLines.runs(new ExpressionTerms.Expression(ExpressionBuilder.rowSum(amount)),
                    icons::nameOf), KEPT_HINT, null);
        }
        if (removable) {
            flow.control(REMOVE, REMOVE_ROW_HINT, at -> apply(b -> b.removeRow(row)));
        }
    }

    // The amount's source, number, prefab or item, the active one plain and the others links, then the operand
    // and its factor.
    private void drawAmount(ControlFlow flow, int row, ExpressionBuilder.Piece piece) {
        flow.text(AMOUNT_FROM, GRAY);
        ExpressionBuilder.Operand operand = piece.operand();
        boolean isNumber = operand instanceof ExpressionBuilder.Num;
        boolean isPrefab = operand instanceof ExpressionBuilder.Ref ref && ref.member() == null;
        flow.choice(NUMBER_MODE, NUMBER_MODE_HINT, isNumber, () -> apply(b -> b.setRowNumber(row,
                ExpressionBuilder.ONE_TEXT)));
        flow.choice(PREFAB_MODE, PREFAB_MODE_HINT, isPrefab, () -> pickRowPrefab(flow.anchor(), row));
        flow.choice(ITEM_MODE, ITEM_MODE_HINT, !isNumber && !isPrefab, () -> pickRowRelative(flow.anchor(), row));
        if (operand instanceof ExpressionBuilder.Num num) {
            flow.control(num.digits(), NUMBER_HINT, at -> overlays.openNumber(at, num.digits(),
                    digits -> apply(b -> b.setRowNumber(row, digits))));
        } else if (isPrefab) {
            flow.runs(operandRuns(piece), PREFAB_HINT, at -> pickRowPrefab(at, row));
        } else {
            flow.runs(operandRuns(piece), REF_HINT, at -> pickRowRelative(at, row));
        }
        drawScale(flow, piece, (op, factor) -> apply(b -> b.setRowScale(row, op, factor)));
    }

    private void pickRowPrefab(BuilderOverlays.Anchor at, int row) {
        overlays.openDropdown(at, SearchDropdown.over(List.of(), scalarPrefabs.get(), false),
                choice -> apply(b -> b.setRowVar(row, choice.id())));
    }

    private void pickRowRelative(BuilderOverlays.Anchor at, int row) {
        overlays.openDropdown(at, SearchDropdown.over(inventory.get(), prefabs.get(), true),
                choice -> overlays.openPicker(at, type -> apply(b -> b.setRowRelative(row, choice, type))));
    }

    private void drawTerms(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        for (int index = 0; index < pieces.size(); index++) {
            flow.newLine();
            drawTerm(flow, index, pieces.get(index), pieces.size());
        }
        flow.newLine();
        flow.control(ADD_NUMBER, ADD_NUMBER_HINT, at -> apply(ExpressionBuilder::addNumber));
        flow.control(ADD_REF, ADD_REF_HINT, at -> overlays.openDropdown(at,
                SearchDropdown.over(inventory.get(), prefabs.get(), true), choice -> apply(b -> b.relate(choice))));
    }

    private void drawTerm(ControlFlow flow, int index, ExpressionBuilder.Piece piece, int count) {
        if (index > 0) {
            boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
            flow.control(plus ? PLUS : MINUS, JOIN_HINT, at -> apply(b -> b.setJoin(index,
                    plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
        }
        drawOperand(flow, index, piece);
        if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
            drawScale(flow, piece, (op, factor) -> apply(b -> b.setScale(index, op, factor)));
        }
        if (piece.operand() instanceof ExpressionBuilder.Ref ref) {
            drawMember(flow, index, ref);
        }
        drawTermEnd(flow, index, count);
    }

    // [( ) with next] before the last term, and [remove] while another term stands.
    private void drawTermEnd(ControlFlow flow, int index, int count) {
        if (index + 1 < count) {
            flow.control(WRAP, WRAP_HINT, at -> apply(b -> b.wrapWithNext(index)));
        }
        if (count > 1) {
            flow.control(REMOVE, REMOVE_TERM_HINT, at -> apply(b -> b.remove(index)));
        }
    }

    private void drawOperand(ControlFlow flow, int index, ExpressionBuilder.Piece piece) {
        switch (piece.operand()) {
            case ExpressionBuilder.Num num -> flow.control(num.digits(), NUMBER_HINT, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setNumber(index, digits))));
            case ExpressionBuilder.Ref _ -> flow.runs(operandRuns(piece), REF_HINT, at -> overlays.openDropdown(at,
                    SearchDropdown.over(inventory.get(), prefabs.get(), true),
                    choice -> apply(b -> b.setRef(index, choice))));
            case ExpressionBuilder.Nest nest -> drawNest(flow, index, nest);
            case ExpressionBuilder.Kept _ -> flow.runs(operandRuns(piece), KEPT_HINT, null);
        }
    }

    private void drawNest(ControlFlow flow, int index, ExpressionBuilder.Nest nest) {
        flow.text(OPEN, WHITE);
        flow.runs(innerRuns(nest), null, null);
        flow.text(CLOSE, WHITE);
        flow.control(UNWRAP, UNWRAP_HINT, at -> apply(b -> b.unwrap(index)));
    }

    // A goo type taken from the item or prefab: its icon picks another or the whole value again.
    private void drawMember(ControlFlow flow, int index, ExpressionBuilder.Ref ref) {
        String member = ref.member();
        if (member == null) {
            flow.control(ONE_TYPE, ONE_TYPE_HINT, at -> overlays.openPicker(at,
                    type -> apply(b -> b.setMember(index, type))));
            return;
        }
        flow.text(ONLY, GRAY);
        flow.typeIcon(member, at -> overlays.openPicker(at, type -> apply(b -> b.setMember(index, type)),
                () -> apply(b -> b.setMember(index, null))));
    }

    private List<ExpressionLines.Run> operandRuns(ExpressionBuilder.Piece piece) {
        return ExpressionLines.runs(new ExpressionTerms.Expression(ExpressionBuilder.operandSum(piece)),
                icons::nameOf);
    }

    private List<ExpressionLines.Run> innerRuns(ExpressionBuilder.Nest nest) {
        return ExpressionLines.runs(new ExpressionTerms.Expression(ExpressionBuilder.rowSum(
                new ExpressionBuilder.Row(ExpressionBuilder.ONE_TEXT, nest.pieces()))), icons::nameOf);
    }

    /**
     * A factor's change.
     */
    @FunctionalInterface
    private interface ScaleChange {
        void changed(ExpressionBuilder.Op op, String factor);
    }

    // "times" or "divided by", a click switching it, then the factor, a click opening it in the number box.
    private void drawScale(ControlFlow flow, ExpressionBuilder.Piece piece, ScaleChange change) {
        boolean multiplies = piece.op() == ExpressionBuilder.Op.MULTIPLY;
        flow.control(multiplies ? TIMES : DIVIDED_BY, OP_HINT, at -> change.changed(
                multiplies ? ExpressionBuilder.Op.DIVIDE : ExpressionBuilder.Op.MULTIPLY, piece.factor()));
        flow.control(piece.factor(), FACTOR_HINT, at -> overlays.openNumber(at, piece.factor(),
                digits -> change.changed(piece.op(), digits)));
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

    private void finish() {
        Runnable finished = done;
        close();
        if (finished != null) {
            finished.run();
        }
    }
}

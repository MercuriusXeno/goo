package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
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
    private static final int PANEL_COLOR = 0x30FFFFFF;
    // Decision every-value-builder-reads-value-kind-on-one-line-defaulting-by-type.
    private static final String VALUE = "Value";
    private static final String NUMBER_KIND = "a number";
    private static final String PER_TYPE = "by type";
    // Decision items-kind-hint-says-worth-based-on-other-items-prefabs: the goo kind reads relative on every tab.
    private static final String GOO_KIND = "relative";
    // decision amount-modes-name-what-the-amount-comes-from
    private static final String NUMBER_MODE = "number";
    private static final String PREFAB_MODE = "prefab";
    private static final String RELATIVE_MODE = "relative";
    private static final String ITS = "its";
    private static final String PLUS = "plus";
    private static final String MINUS = "minus";
    private static final String RATIO = ":";
    private static final String WHOLE_VALUE = "the whole value";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    private static final String ONE_TYPE = "only one goo type";
    private static final String ONLY = "only";
    // decision remove-draws-as-a-red-x
    private static final String REMOVE = "X";
    // The operator's ruling: every X's tooltip reads the one word Delete, and no other control explains itself.
    private static final String DELETE_HINT = "Delete";
    private static final String ADD_TYPE = "+";
    private static final String ADD_NUMBER = "+ number";
    private static final String ADD_ITEM_OR_PREFAB = "+ item or prefab";
    private static final String ADD_PREFAB = "+ prefab";
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
        ControlFlow flow = new ControlFlow(graphics, font, targets, icons,
                new ControlFlow.Span(area.left(), area.right(), top), actions::openVariable);
        drawKind(flow, edited);
        drawValue(flow, edited);
        flow.newLine();
        // Decision value-expression-boxes-take-a-cancel-button: done draws only once a change is fully in, and
        // cancel always draws, so a partial or mistaken edit is always one click from discarded.
        if (edited.written().filter(written -> !written.equals(openedText)).isPresent()) {
            flow.coloredControl(DONE, GREEN, null, at -> finish());
        }
        flow.coloredControl(CANCEL, RED, null, at -> cancel());
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
            flow.note(UNREAD, RED);
        }
    }

    // A number is a prefab's kind alone, and a goo value is offered only where the value has something to read.
    private void drawKind(ControlFlow flow, ExpressionBuilder edited) {
        flow.text(VALUE, GRAY);
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

    // A goo value starts from what it held before, or from the item or prefab picked first.
    private void switchToGoo(BuilderOverlays.Anchor at, ExpressionBuilder edited) {
        if (edited.holdsKept(BuilderGrammar.GOO_SORT)) {
            apply(b -> b.switchToWhole(BuilderGrammar.GOO_SORT, List.of()));
            return;
        }
        overlays.openDropdown(at, termList(BuilderGrammar.GOO_SORT), choice -> apply(b -> b.switchToWhole(
                BuilderGrammar.GOO_SORT, List.of(ExpressionBuilder.firstTerm(refOf(choice))))));
    }

    private void drawRows(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Row> rows = edited.rows();
        Set<String> held = BuilderGrammar.heldTypes(edited);
        for (int index = 0; index < rows.size(); index++) {
            flow.newLine();
            drawRow(flow, index, rows.get(index), rows.size() > 1, held);
        }
        if (!rows.isEmpty()) {
            flow.newLine();
        }
        if (held.size() < GooTypes.order().size()) {
            flow.control(ADD_TYPE, null, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type)),
                    null, held));
        }
    }

    private void drawRow(ControlFlow flow, int row, ExpressionBuilder.Row amount, boolean removable,
                         Set<String> held) {
        flow.typeIcon(amount.type(), at -> overlays.openPicker(at, type -> apply(b -> b.setRowType(row, type)),
                null, held));
        ExpressionBuilder.Piece piece = amount.pieces().getFirst();
        if (amount.pieces().size() == 1 && !(piece.operand() instanceof ExpressionBuilder.Kept)) {
            drawAmount(flow, row, amount.type(), piece);
        } else {
            flow.runs(ExpressionLines.runs(new ExpressionTerms.Expression(ExpressionBuilder.rowSum(amount)),
                    icons::nameOf), null, null);
        }
        if (removable) {
            flow.coloredControl(REMOVE, RED, DELETE_HINT, at -> apply(b -> b.removeRow(row)));
        }
    }

    // The amount's source, number, prefab or a goo type of another value, each offered only where the value has
    // one to read, then the operand and its factor.
    private void drawAmount(ControlFlow flow, int row, String type, ExpressionBuilder.Piece piece) {
        flow.choices(amountModes(row, type, piece.operand()), overlays);
        if (drawAmountOperand(flow, row, type, piece.operand())) {
            drawFactor(flow, piece, (op, factor) -> apply(b -> b.setRowScale(row, op, factor)));
        }
    }

    private ControlFlow.ChoiceSet amountModes(int row, String type, ExpressionBuilder.Operand operand) {
        boolean isNumber = operand instanceof ExpressionBuilder.Num;
        boolean isPrefab = isScalarPrefab(operand);
        ControlFlow.ChoiceSet modes = new ControlFlow.ChoiceSet();
        modes.add(numberMode(row, isNumber), isNumber);
        if (isPrefab || !grammar.scalarPrefabs().isEmpty()) {
            modes.add(new BuilderOverlays.Option(PREFAB_MODE, null, at -> pickRowPrefab(at, row)),
                    isPrefab);
        }
        boolean isMember = !isNumber && !isPrefab;
        if (isMember || hasRowMemberChoices()) {
            modes.add(memberMode(row, type), isMember);
        }
        return modes;
    }

    private static boolean isScalarPrefab(ExpressionBuilder.Operand operand) {
        return operand instanceof ExpressionBuilder.Ref ref && ref.member() == null;
    }

    // Picking the number it already is keeps the number rather than resetting it to 1.
    private BuilderOverlays.Option numberMode(int row, boolean isNumber) {
        return new BuilderOverlays.Option(NUMBER_MODE, null, at -> {
            if (!isNumber) {
                apply(b -> b.setRowNumber(row, ExpressionBuilder.ONE_TEXT));
            }
        });
    }

    private BuilderOverlays.Option memberMode(int row, String type) {
        return new BuilderOverlays.Option(RELATIVE_MODE, null, at -> pickRowMember(at, row, type));
    }

    // The amount's number, prefab, or item or prefab with the goo type taken from it; answers whether it drew one.
    private boolean drawAmountOperand(ControlFlow flow, int row, String type, ExpressionBuilder.Operand operand) {
        switch (operand) {
            case ExpressionBuilder.Num num -> flow.control(num.digits(), null, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setRowNumber(row, digits))));
            case ExpressionBuilder.Ref ref when ref.member() == null -> flow.runs(refRuns(ref), null,
                    at -> pickRowPrefab(at, row));
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), null, at -> pickRowMember(at, row, type));
                flow.text(ITS, GRAY);
                flow.typeIcon(ref.member(), at -> overlays.openPicker(at, member -> apply(b -> b.setRowMember(row,
                        member))));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void pickRowPrefab(BuilderOverlays.Anchor at, int row) {
        overlays.openDropdown(at, SearchDropdown.over(List.of(), grammar.scalarPrefabs(), false),
                choice -> apply(b -> b.setRowVar(row, choice.id())));
    }

    // The row's own goo type is the one taken from what is picked; its icon after it picks another.
    private void pickRowMember(BuilderOverlays.Anchor at, int row, String type) {
        boolean items = grammar.readsItems();
        overlays.openDropdown(at, SearchDropdown.over(items ? inventory.get() : List.of(), grammar.perTypePrefabs(),
                items), choice -> apply(b -> b.setRowRelative(row, choice, type)));
    }

    private boolean hasRowMemberChoices() {
        return grammar.readsItems() || !grammar.perTypePrefabs().isEmpty();
    }

    private void drawTerms(ControlFlow flow, ExpressionBuilder edited) {
        String sort = grammar.sortOf(edited);
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        drawSum(flow, List.of(), pieces, sort);
        // Decision scale-shows-the-ratio-at-once: several terms draw the whole value's ratio 1:1 on a line of its
        // own; setting a side wraps the value as one sum scaled by it.
        if (pieces.size() > 1) {
            flow.newLine();
            flow.text(WHOLE_VALUE, GRAY);
            drawRatio(flow, ExpressionBuilder.ONE_TEXT, ExpressionBuilder.ONE_TEXT,
                    (op, factor) -> apply(b -> b.scaleWhole(op, factor)));
        }
    }

    // A sum's terms, a line each, then what may join them.
    private void drawSum(ControlFlow flow, List<Integer> sum, List<ExpressionBuilder.Piece> pieces, String sort) {
        for (int index = 0; index < pieces.size(); index++) {
            flow.newLine();
            drawTerm(flow, placeOf(sum, index), pieces.get(index), pieces.size(), sort);
        }
        flow.newLine();
        if (grammar.termTakesNumbers(sort)) {
            flow.control(ADD_NUMBER, null, at -> apply(b -> b.addNumber(sum)));
        }
        if (hasTermChoices(sort)) {
            boolean items = grammar.termTakesItems(sort);
            flow.control(items ? ADD_ITEM_OR_PREFAB : ADD_PREFAB, null,
                    at -> overlays.openDropdown(at, termList(sort), choice -> apply(b -> b.relate(sum, choice))));
        }
    }

    private void drawTerm(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, int count,
                          String sort) {
        if (path.getLast() > 0) {
            drawJoin(flow, path, piece);
        }
        if (piece.operand() instanceof ExpressionBuilder.Nest nest) {
            drawNest(flow, path, piece, nest, count, sort);
            return;
        }
        drawOperand(flow, path, piece, sort);
        if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
            drawFactor(flow, piece, (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        }
        if (count > 1) {
            flow.coloredControl(REMOVE, RED, DELETE_HINT, at -> apply(b -> b.remove(path)));
        }
    }

    private void drawJoin(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece) {
        boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
        flow.control(plus ? PLUS : MINUS, null, at -> apply(b -> b.setJoin(path,
                plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
    }

    private void drawOperand(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, String sort) {
        switch (piece.operand()) {
            case ExpressionBuilder.Num num -> flow.control(num.digits(), null, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setNumber(path, digits))));
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), null, at -> overlays.openDropdown(at, termList(sort),
                        choice -> apply(b -> b.setRef(path, choice))));
                if (grammar.memberOptional(sort)) {
                    drawMember(flow, path, ref);
                }
            }
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ -> flow.runs(operandRuns(piece), null,
                    null);
        }
    }

    // A "(" line, its terms indented one step past the "(", each edited in place, then a ")" line under the "("
    // carrying the parenthetical's factor.
    // Decision sum-of-terms-leaves-the-builder.
    private void drawNest(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece,
                          ExpressionBuilder.Nest nest, int count, String sort) {
        int column = flow.column();
        flow.text(OPEN_PAREN, GRAY);
        if (count > 1) {
            flow.coloredControl(REMOVE, RED, DELETE_HINT, at -> apply(b -> b.remove(path)));
        }
        flow.indent(column + ControlFlow.INDENT);
        drawSum(flow, path, nest.pieces(), sort);
        flow.outdent(ControlFlow.INDENT);
        flow.text(CLOSE_PAREN, GRAY);
        drawFactor(flow, piece, (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        flow.outdent(column);
    }

    // A goo type taken from the item or prefab: its icon picks another or the whole value again.
    private void drawMember(ControlFlow flow, List<Integer> path, ExpressionBuilder.Ref ref) {
        String member = ref.member();
        if (member == null) {
            flow.control(ONE_TYPE, null, at -> overlays.openPicker(at,
                    type -> apply(b -> b.setMember(path, type))));
            return;
        }
        flow.text(ONLY, GRAY);
        flow.typeIcon(member, at -> overlays.openPicker(at, type -> apply(b -> b.setMember(path, type)),
                () -> apply(b -> b.setMember(path, null))));
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

    private boolean hasTermChoices(String sort) {
        return grammar.termTakesItems(sort) || !grammar.termPrefabs(sort).isEmpty();
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

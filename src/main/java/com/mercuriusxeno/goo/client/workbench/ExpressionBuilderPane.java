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
    private static final String NUMBER_KIND = "a number";
    private static final String PER_TYPE = "by type";
    // Decision items-kind-hint-says-worth-based-on-other-items-prefabs: the goo kind reads relative on every tab.
    private static final String GOO_KIND = "relative";
    // Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators.
    private static final String PLUS_SIGN = "+";
    private static final String MINUS_SIGN = "-";
    private static final String SUM_ADD = "+/-";
    private static final String EQUALS = "=";
    private static final String POSSESSIVE = "'s";
    private static final String WRAP = "()";
    private static final String MOVE_UP = "^";
    private static final String MOVE_DOWN = "v";
    private static final int EARLIER = -1;
    private static final int LATER = 1;
    private static final String RATIO = ":";
    private static final String WHOLE_VALUE = "the whole value";
    private static final String OPEN_PAREN = "(";
    private static final String CLOSE_PAREN = ")";
    // decision remove-draws-as-a-red-x
    private static final String REMOVE = "X";
    // The operator's ruling: every X's tooltip reads the one word Delete, and no other control explains itself.
    private static final String DELETE_HINT = "Delete";
    private static final String ADD_TYPE = "+";
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
        drawClosing(flow, edited);
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
        if (perType) {
            drawAddType(flow, edited);
        }
    }

    private void drawAddType(ControlFlow flow, ExpressionBuilder edited) {
        Set<String> held = BuilderGrammar.heldTypes(edited);
        if (held.size() < GooTypes.order().size()) {
            flow.control(ADD_TYPE, null, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type)),
                    null, held));
        }
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

    // Each row's head one indent in, its terms a second indent in.
    private void drawRows(ControlFlow flow, ExpressionBuilder edited) {
        List<ExpressionBuilder.Row> rows = edited.rows();
        Set<String> held = BuilderGrammar.heldTypes(edited);
        for (int index = 0; index < rows.size(); index++) {
            ExpressionBuilder.Row row = rows.get(index);
            flow.indent();
            drawRowHead(flow, index, row, rows.size() > 1, held);
            drawSum(flow, List.of(index), row.pieces(), new Place(BuilderGrammar.NUMBER_SORT, row.type()));
            flow.outdent(ControlFlow.INDENT);
        }
    }

    // [type] = with the row's X at the line's right end.
    private void drawRowHead(ControlFlow flow, int row, ExpressionBuilder.Row amount, boolean removable,
                             Set<String> held) {
        flow.typeIcon(amount.type(), at -> overlays.openPicker(at, type -> apply(b -> b.setRowType(row, type)),
                null, held));
        flow.text(EQUALS, GRAY);
        if (removable) {
            flow.alignRight(ButtonPainter.width(flow.font(), REMOVE));
            flow.coloredControl(REMOVE, RED, DELETE_HINT, at -> apply(b -> b.removeRow(row)));
        }
    }

    private void drawTerms(ControlFlow flow, ExpressionBuilder edited) {
        String sort = grammar.sortOf(edited);
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        drawSum(flow, List.of(), pieces, new Place(sort, null));
        // Decision scale-shows-the-ratio-at-once: several terms draw the whole value's ratio 1:1 on a line of its
        // own; setting a side wraps the value as one sum scaled by it.
        if (pieces.size() > 1) {
            flow.newLine();
            flow.text(WHOLE_VALUE, GRAY);
            drawRatio(flow, ExpressionBuilder.ONE_TEXT, ExpressionBuilder.ONE_TEXT,
                    (op, factor) -> apply(b -> b.scaleWhole(op, factor)));
        }
    }

    // A sum's terms one indent in, a line each, then the [+/-] that adds one.
    private void drawSum(ControlFlow flow, List<Integer> sum, List<ExpressionBuilder.Piece> pieces, Place place) {
        flow.indent();
        for (int index = 0; index < pieces.size(); index++) {
            flow.newLine();
            drawTerm(flow, placeOf(sum, index), pieces.get(index), pieces.size(), place);
        }
        flow.newLine();
        drawSumAdd(flow, sum, place);
        flow.outdent(ControlFlow.INDENT);
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

    // [join] where it follows another term, the operand, its ratio, then [()] [^] [v] [x].
    private void drawTerm(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, int count,
                          Place place) {
        if (path.getLast() > 0) {
            drawJoin(flow, path, piece);
        }
        if (piece.operand() instanceof ExpressionBuilder.Nest nest) {
            drawNest(flow, path, piece, nest, count, place);
            return;
        }
        drawOperand(flow, path, piece, place);
        if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
            drawFactor(flow, piece, (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        }
        flow.control(WRAP, null, at -> apply(b -> b.wrap(path)));
        drawOrderAndRemove(flow, path, count);
    }

    // A term draws ^ and v only where the move keeps the sum's value: the first draws no ^, the last no v, and none
    // lands a subtracted term first; a sum keeps its last term.
    private void drawOrderAndRemove(ControlFlow flow, List<Integer> path, int count) {
        ExpressionBuilder edited = builder;
        if (edited != null && edited.canMove(path, EARLIER)) {
            flow.control(MOVE_UP, null, at -> apply(b -> b.move(path, EARLIER)));
        }
        if (edited != null && edited.canMove(path, LATER)) {
            flow.control(MOVE_DOWN, null, at -> apply(b -> b.move(path, LATER)));
        }
        if (count > 1) {
            flow.coloredControl(REMOVE, RED, DELETE_HINT, at -> apply(b -> b.remove(path)));
        }
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
            case ExpressionBuilder.Num num -> flow.control(num.digits(), null, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setNumber(path, digits))));
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), null, at -> overlays.openDropdown(at, termList(place),
                        choice -> apply(b -> b.setOperand(path, refFor(choice, place)))));
                drawMember(flow, path, ref, place);
            }
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ -> flow.runs(operandRuns(piece), null,
                    null);
        }
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

    // A "(" line after the term's join carrying its ^ v x, its terms one step past the "(", each edited in place,
    // then a ")" line under the "(" carrying the parenthetical's factor.
    // Decision sum-of-terms-leaves-the-builder.
    private void drawNest(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece,
                          ExpressionBuilder.Nest nest, int count, Place place) {
        int column = flow.column();
        flow.text(OPEN_PAREN, GRAY);
        drawOrderAndRemove(flow, path, count);
        flow.shift(column);
        drawSum(flow, path, nest.pieces(), place);
        flow.newLine();
        flow.text(CLOSE_PAREN, GRAY);
        drawFactor(flow, piece, (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        flow.shift(-column);
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

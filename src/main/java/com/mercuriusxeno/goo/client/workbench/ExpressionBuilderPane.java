package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
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
 * {@link BuilderGrammar} answers, so no choice ends in an error: a number stands unscaled, a reference offers a
 * factor, the goo type picker hides the types a value holds, and a list holds only what the value may read. Several
 * terms stand as one sum, drawn as "the sum of:" with its terms indented below it, each edited in place, and the
 * whole value scales as one sum; these are the two ways the game's files use parentheses. Every control names what
 * it does on hover, the lines wrap inside the pane, and done closes the builder below the value. Each change
 * writes the string the file grammar reads; a value is never written empty. The state lives in
 * {@link ExpressionBuilder}.
 */
final class ExpressionBuilderPane {
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int PANEL_COLOR = 0x30FFFFFF;
    private static final String KIND = "Kind:";
    private static final String NUMBER_KIND = "a number";
    private static final String NUMBER_KIND_HINT = "One number: a count, or a sum of numbers and the number prefabs"
            + " above this one";
    private static final String PER_TYPE = "per goo type";
    private static final String PER_TYPE_HINT = "A separate amount for each goo type";
    private static final String ITEMS_GOO_KIND = "from other items";
    // decision items-kind-hint-says-worth-based-on-other-items-prefabs
    private static final String ITEMS_GOO_KIND_HINT = "worth based on other items/prefabs";
    private static final String PREFABS_GOO_KIND = "from other prefabs";
    private static final String PREFABS_GOO_KIND_HINT = "Worth what the per-type prefabs above this one are worth,"
            + " scaled and summed";
    private static final String AMOUNT_FROM = "amount from";
    // decision amount-modes-name-what-the-amount-comes-from
    private static final String NUMBER_MODE = "a number";
    private static final String NUMBER_MODE_HINT = "The amount is a number you set";
    private static final String PREFAB_MODE = "a number prefab";
    private static final String PREFAB_MODE_HINT = "The amount is a number prefab's value";
    private static final String ITEM_MODE = "a goo type of an item or prefab";
    private static final String ITEM_MODE_HINT = "The amount is one goo type of an item's or per-type prefab's value";
    private static final String PREFAB_TYPE_MODE = "a goo type of a prefab";
    private static final String PREFAB_TYPE_MODE_HINT = "The amount is one goo type of a per-type prefab's value";
    private static final String ITS = "its";
    private static final String NUMBER_HINT = "Click to change the number";
    private static final String PREFAB_HINT = "Click to pick a different number prefab";
    private static final String REF_HINT = "Click to pick a different one";
    private static final String PLUS = "plus";
    private static final String MINUS = "minus";
    private static final String JOIN_HINT = "Click to switch between adding and subtracting this";
    private static final String RATIO = ":";
    private static final String MULTIPLIER_HINT = "Click to change what this is multiplied by; 1 removes it";
    private static final String DIVISOR_HINT = "Click to change what this is divided by; 1 removes it";
    private static final String SCALE = "scale";
    private static final String SCALE_HINT = "Multiply or divide this by a number";
    private static final String SCALE_SUM = "scale the sum";
    private static final String SCALE_SUM_HINT = "Multiply or divide the whole sum by a number";
    private static final String SCALE_WHOLE = "scale the whole value";
    private static final String SCALE_WHOLE_HINT = "Multiply or divide everything above by one number, as one sum";
    private static final String SUM_OF = "the sum of:";
    private static final String ONE_TYPE = "only one goo type";
    private static final String ONE_TYPE_HINT = "Use only one goo type of this value";
    private static final String ONLY = "only";
    private static final String KEPT_HINT = "No control builds this term; remove it to rebuild it";
    private static final String REMOVE = "remove";
    private static final String REMOVE_TERM_HINT = "Remove this term";
    private static final String REMOVE_SUM_HINT = "Remove this sum and every term in it";
    private static final String REMOVE_ROW_HINT = "Remove this goo type's amount";
    private static final String ADD_TYPE = "+ goo type";
    private static final String ADD_TYPE_HINT = "Add an amount for another goo type";
    private static final String ADD_NUMBER = "+ number";
    private static final String ADD_NUMBER_HINT = "Add a number";
    private static final String ADD_ITEM_OR_PREFAB = "+ item or prefab";
    private static final String ADD_ITEM_OR_PREFAB_HINT = "Add what an item or per-type prefab is worth";
    private static final String ADD_PREFAB = "+ prefab";
    private static final String ADD_PREFAB_HINT = "Add a prefab's value";
    private static final String ADD_SUM = "+ sum of terms";
    private static final String ADD_SUM_HINT = "Add several terms as one, to subtract or scale them together";
    private static final String PICK_TYPE = "Pick the first goo type:";
    private static final String UNREAD = "This value cannot be read; pick a kind to start over.";
    private static final String EMPTY = "No value yet: pick a kind to give it one.";
    private static final String DENIED = "Denied: this holds no goo. Pick a kind to give it a value.";
    private static final String DONE = "done";
    // decision done-hint-reads-no-unsaved-changes-or-changes-already-saved
    private static final String NO_CHANGES_HINT = "No unsaved changes";
    private static final String CHANGES_SAVED_HINT = "Changes already saved to the draft";
    private static final String BLANK = "";
    private static final String TERM_MARK = "term ";
    private static final String ROW_MARK = "row ";

    private final ItemIcons icons;
    private final BuilderOverlays overlays;
    private final Supplier<List<ValuesTab.ManagerEntry>> inventory;
    private final Supplier<List<BuilderGrammar.Prefab>> prefabs;
    private BuilderGrammar grammar;
    private @Nullable ExpressionBuilder builder;
    private String openedText = BLANK;
    private @Nullable Consumer<String> writes;
    private @Nullable Runnable done;
    // The pieces whose scale offer was clicked, drawn as the ratio 1:1 until the builder closes or a piece is removed.
    private final Set<String> openedFactors = new HashSet<>();

    /**
     * @param actions   the panes' line back to the screen
     * @param icons     the items' icons
     * @param inventory the server's items, which item terms and amounts list
     * @param prefabs   every prefab, in the file's order
     */
    ExpressionBuilderPane(ManagerActions actions, ItemIcons icons, Supplier<List<ValuesTab.ManagerEntry>> inventory,
                          Supplier<List<BuilderGrammar.Prefab>> prefabs) {
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
        openedFactors.clear();
        grammar = read;
        ExpressionBuilder opened = ExpressionBuilder.of(shown);
        if (!opened.isPerType() && !opened.terms().isEmpty()) {
            opened.nameWholeSort(read.sortOf(opened));
        }
        builder = opened;
        // The builder's own text, not the shown string, so its normalized rewrite reads as no change.
        openedText = opened.text();
        writes = change;
        done = finish;
    }

    /**
     * Closes the builder, keeping what it wrote.
     */
    void close() {
        overlays.close();
        openedFactors.clear();
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
        ControlFlow flow = new ControlFlow(graphics, font, targets, icons, area.left(), area.right(), top);
        drawKind(flow, edited);
        drawValue(flow, edited);
        flow.newLine();
        flow.control(DONE, doneHint(openedText, edited.text()), at -> finish());
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

    // A number is a prefab's kind alone, and a goo value is offered only where the value has something to read.
    private void drawKind(ControlFlow flow, ExpressionBuilder edited) {
        flow.text(KIND, GRAY);
        boolean perType = edited.isPerType();
        String sort = perType || edited.terms().isEmpty() ? BLANK : grammar.sortOf(edited);
        ControlFlow.ChoiceSet kinds = new ControlFlow.ChoiceSet();
        if (grammar.offersNumberKind()) {
            kinds.add(new BuilderOverlays.Option(NUMBER_KIND, NUMBER_KIND_HINT,
                    at -> apply(ExpressionBuilder::switchToWhole)), BuilderGrammar.NUMBER_SORT.equals(sort));
        }
        kinds.add(new BuilderOverlays.Option(PER_TYPE, PER_TYPE_HINT, at -> apply(ExpressionBuilder::switchToPerType)),
                perType);
        if (grammar.offersGooKind()) {
            boolean items = grammar.readsItems();
            kinds.add(new BuilderOverlays.Option(items ? ITEMS_GOO_KIND : PREFABS_GOO_KIND, gooKindHint(items),
                    at -> switchToGoo(at, edited)), BuilderGrammar.GOO_SORT.equals(sort));
        }
        flow.choices(kinds, overlays);
    }

    /**
     * @param readsItems whether the value reads items, as an item's or group's does
     * @return the hover hint of the goo kind choice: the Items tab's "from other items" or a prefab's "from other
     *         prefabs"
     */
    static String gooKindHint(boolean readsItems) {
        return readsItems ? ITEMS_GOO_KIND_HINT : PREFABS_GOO_KIND_HINT;
    }

    /**
     * @param opened  the text the builder opened with
     * @param written the text the builder writes now
     * @return the done control's hover hint: whether the builder holds no change, or holds changes the draft already
     *         has
     */
    static String doneHint(String opened, String written) {
        return opened.equals(written) ? NO_CHANGES_HINT : CHANGES_SAVED_HINT;
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
        flow.newLine();
        if (rows.isEmpty()) {
            flow.text(PICK_TYPE, GRAY);
        }
        if (held.size() < GooTypes.order().size()) {
            flow.control(ADD_TYPE, ADD_TYPE_HINT, at -> overlays.openPicker(at, type -> apply(b -> b.addType(type)),
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
                    icons::nameOf), KEPT_HINT, null);
        }
        if (removable) {
            flow.control(REMOVE, REMOVE_ROW_HINT, at -> removePiece(b -> b.removeRow(row)));
        }
    }

    // The amount's source, number, prefab or a goo type of another value, each offered only where the value has
    // one to read, then the operand and its factor.
    private void drawAmount(ControlFlow flow, int row, String type, ExpressionBuilder.Piece piece) {
        flow.text(AMOUNT_FROM, GRAY);
        flow.choices(amountModes(row, type, piece.operand()), overlays);
        if (drawAmountOperand(flow, row, type, piece.operand())) {
            drawFactor(flow, piece, ScaleOffer.row(row), (op, factor) -> apply(b -> b.setRowScale(row, op, factor)));
        }
    }

    private ControlFlow.ChoiceSet amountModes(int row, String type, ExpressionBuilder.Operand operand) {
        boolean isNumber = operand instanceof ExpressionBuilder.Num;
        boolean isPrefab = isScalarPrefab(operand);
        ControlFlow.ChoiceSet modes = new ControlFlow.ChoiceSet();
        modes.add(numberMode(row, isNumber), isNumber);
        if (isPrefab || !grammar.scalarPrefabs().isEmpty()) {
            modes.add(new BuilderOverlays.Option(PREFAB_MODE, PREFAB_MODE_HINT, at -> pickRowPrefab(at, row)),
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
        return new BuilderOverlays.Option(NUMBER_MODE, NUMBER_MODE_HINT, at -> {
            if (!isNumber) {
                apply(b -> b.setRowNumber(row, ExpressionBuilder.ONE_TEXT));
            }
        });
    }

    private BuilderOverlays.Option memberMode(int row, String type) {
        boolean items = grammar.readsItems();
        return new BuilderOverlays.Option(items ? ITEM_MODE : PREFAB_TYPE_MODE,
                items ? ITEM_MODE_HINT : PREFAB_TYPE_MODE_HINT, at -> pickRowMember(at, row, type));
    }

    // The amount's number, prefab, or item or prefab with the goo type taken from it; answers whether it drew one.
    private boolean drawAmountOperand(ControlFlow flow, int row, String type, ExpressionBuilder.Operand operand) {
        switch (operand) {
            case ExpressionBuilder.Num num -> flow.control(num.digits(), NUMBER_HINT, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setRowNumber(row, digits))));
            case ExpressionBuilder.Ref ref when ref.member() == null -> flow.runs(refRuns(ref), PREFAB_HINT,
                    at -> pickRowPrefab(at, row));
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), REF_HINT, at -> pickRowMember(at, row, type));
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
        if (grammar.termTakesNumbers(sort)) {
            flow.control(ADD_SUM, ADD_SUM_HINT, at -> apply(b -> b.addSum(new ExpressionBuilder.Num(
                    ExpressionBuilder.ONE_TEXT))));
        } else if (hasTermChoices(sort)) {
            flow.control(ADD_SUM, ADD_SUM_HINT, at -> overlays.openDropdown(at, termList(sort),
                    choice -> apply(b -> b.addSum(refOf(choice)))));
        }
        if (pieces.size() > 1) {
            flow.control(SCALE_WHOLE, SCALE_WHOLE_HINT, at -> overlays.openNumber(at, BLANK,
                    factor -> apply(b -> b.scaleWhole(factor)), true));
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
            flow.control(ADD_NUMBER, ADD_NUMBER_HINT, at -> apply(b -> b.addNumber(sum)));
        }
        if (hasTermChoices(sort)) {
            boolean items = grammar.termTakesItems(sort);
            flow.control(items ? ADD_ITEM_OR_PREFAB : ADD_PREFAB, items ? ADD_ITEM_OR_PREFAB_HINT : ADD_PREFAB_HINT,
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
            drawFactor(flow, piece, ScaleOffer.term(path), (op, factor) -> apply(b -> b.setScale(path, op, factor)));
        }
        if (count > 1) {
            flow.control(REMOVE, REMOVE_TERM_HINT, at -> removePiece(b -> b.remove(path)));
        }
    }

    private void drawJoin(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece) {
        boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
        flow.control(plus ? PLUS : MINUS, JOIN_HINT, at -> apply(b -> b.setJoin(path,
                plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
    }

    private void drawOperand(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece, String sort) {
        switch (piece.operand()) {
            case ExpressionBuilder.Num num -> flow.control(num.digits(), NUMBER_HINT, at -> overlays.openNumber(at,
                    num.digits(), digits -> apply(b -> b.setNumber(path, digits))));
            case ExpressionBuilder.Ref ref -> {
                flow.runs(refRuns(ref), REF_HINT, at -> overlays.openDropdown(at, termList(sort),
                        choice -> apply(b -> b.setRef(path, choice))));
                if (grammar.memberOptional(sort)) {
                    drawMember(flow, path, ref);
                }
            }
            case ExpressionBuilder.Nest _, ExpressionBuilder.Kept _ -> flow.runs(operandRuns(piece), KEPT_HINT,
                    null);
        }
    }

    // "the sum of:" with its terms indented below it, each edited in place, then the sum's factor.
    private void drawNest(ControlFlow flow, List<Integer> path, ExpressionBuilder.Piece piece,
                          ExpressionBuilder.Nest nest, int count, String sort) {
        flow.text(SUM_OF, GRAY);
        if (count > 1) {
            flow.control(REMOVE, REMOVE_SUM_HINT, at -> removePiece(b -> b.remove(path)));
        }
        flow.indent();
        drawSum(flow, path, nest.pieces(), sort);
        flow.outdent();
        drawFactor(flow, piece, ScaleOffer.sum(path), (op, factor) -> apply(b -> b.setScale(path, op, factor)));
    }

    // A goo type taken from the item or prefab: its icon picks another or the whole value again.
    private void drawMember(ControlFlow flow, List<Integer> path, ExpressionBuilder.Ref ref) {
        String member = ref.member();
        if (member == null) {
            flow.control(ONE_TYPE, ONE_TYPE_HINT, at -> overlays.openPicker(at,
                    type -> apply(b -> b.setMember(path, type))));
            return;
        }
        flow.text(ONLY, GRAY);
        flow.typeIcon(member, at -> overlays.openPicker(at, type -> apply(b -> b.setMember(path, type)),
                () -> apply(b -> b.setMember(path, null))));
    }

    /**
     * A factor's change.
     */
    @FunctionalInterface
    private interface ScaleChange {
        void changed(ExpressionBuilder.Op op, String factor);
    }

    /**
     * A piece's scale offer.
     *
     * @param label what the offer reads
     * @param hint  what clicking it does
     * @param piece the piece it scales, a term's or sum's path or a goo type row, kept while its ratio stands opened
     */
    private record ScaleOffer(String label, String hint, String piece) {
        static ScaleOffer term(List<Integer> path) {
            return new ScaleOffer(SCALE, SCALE_HINT, TERM_MARK + path);
        }

        static ScaleOffer sum(List<Integer> path) {
            return new ScaleOffer(SCALE_SUM, SCALE_SUM_HINT, TERM_MARK + path);
        }

        static ScaleOffer row(int row) {
            return new ScaleOffer(SCALE, SCALE_HINT, ROW_MARK + row);
        }
    }

    // A number shows no factor, a reference or a sum offers one, and a factor other than 1 reads as a ratio,
    // multiplier:divisor, one side the factor and the other 1, each a number box; setting a side multiplies or
    // divides by it (decision factor-reads-as-a-ratio).
    // Decision scale-shows-the-ratio-at-once: clicking the offer draws 1:1, so either side is set first; a ratio
    // back at 1:1 folds to the offer once the builder opens again.
    private void drawFactor(ControlFlow flow, ExpressionBuilder.Piece piece, ScaleOffer offer, ScaleChange change) {
        BuilderGrammar.Factor factor = BuilderGrammar.factorOf(piece);
        if (factor == BuilderGrammar.Factor.OFFERED && !openedFactors.contains(offer.piece())) {
            flow.control(offer.label(), offer.hint(), at -> openedFactors.add(offer.piece()));
        } else if (factor != BuilderGrammar.Factor.HIDDEN) {
            boolean multiplies = piece.op() == ExpressionBuilder.Op.MULTIPLY;
            ratioSide(flow, multiplies ? piece.factor() : ExpressionBuilder.ONE_TEXT, MULTIPLIER_HINT,
                    ExpressionBuilder.Op.MULTIPLY, change);
            flow.text(RATIO, GRAY);
            ratioSide(flow, multiplies ? ExpressionBuilder.ONE_TEXT : piece.factor(), DIVISOR_HINT,
                    ExpressionBuilder.Op.DIVIDE, change);
        }
    }

    private void ratioSide(ControlFlow flow, String shown, String hint, ExpressionBuilder.Op op, ScaleChange change) {
        // A side committed unchanged changes nothing, so the 1 opposite the factor never drops it.
        flow.control(shown, hint, at -> overlays.openNumber(at, shown, factor -> {
            if (!factor.equals(shown)) {
                change.changed(op, factor);
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

    // A removal shifts the paths and rows after it, so no opened ratio outlives it.
    private void removePiece(Consumer<ExpressionBuilder> removal) {
        openedFactors.clear();
        apply(removal);
    }

    private void finish() {
        Runnable finished = done;
        close();
        if (finished != null) {
            finished.run();
        }
    }
}

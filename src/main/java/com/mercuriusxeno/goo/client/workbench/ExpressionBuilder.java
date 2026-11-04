package com.mercuriusxeno.goo.client.workbench;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An entry's value as the expression builder edits it, with no Screen: per-type rows, each a goo type and its
 * amount, or whole-value terms related to items and prefabs, each term an operand multiplied or divided by a factor
 * and joined by plus or minus, parentheses nesting terms; every control edits the terms and {@link #text()} writes
 * the string the file grammar reads, as the edit box did, so no value is typed (decision
 * expression-builder-composes-every-form-without-typing). A term no control builds is kept whole and written as
 * loaded.
 */
final class ExpressionBuilder {
    /** The factor that scales nothing, and a fresh row's amount. */
    static final String ONE_TEXT = "1";
    private static final String ONE = ONE_TEXT;
    private static final String DENIED = "denied";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String OBJECT_START = "{";
    private static final String OBJECT_END = "}";
    private static final String ROW_SEPARATOR = ", ";
    private static final String QUOTE = "\"";
    private static final String KEY_END = "\": \"";
    private static final String NOTHING = "";
    private static final int PAIR = 2;
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final String ROWS_REFUSE_TERMS = "This entry holds per-type rows, so a whole-value term cannot"
            + " join them: remove the rows first.";
    private static final String TERMS_REFUSE_ROWS = "This entry holds whole-value terms, so a per-type row cannot"
            + " join them: remove the terms first.";
    private static final String TYPE_TAKEN = "This entry already holds a row for that goo type.";
    private static final String NOT_A_NUMBER = "A number box holds digits alone.";
    private static final String MOVE_CHANGES_VALUE = "A first term takes no sign, so a subtracted term cannot move"
            + " first: change its sign first.";
    /** The whole kind holding one number, the kind {@link #switchToWhole()} starts. */
    static final String NUMBER_SORT = "number";

    private final List<Piece> whole = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, List<Piece>> keptWhole = new HashMap<>();
    private List<Row> keptRows = List.of();
    private String wholeSort = NUMBER_SORT;
    private boolean perTypeChosen;
    private boolean denied;
    private @Nullable String unread;
    private @Nullable String refusal;

    /**
     * The side of a term's ratio a number sets.
     */
    enum Op {
        /** The multiplier, written before the operand as a count. */
        MULTIPLY,
        /** The divisor, written after the operand. */
        DIVIDE
    }

    /**
     * What a term stands on.
     */
    sealed interface Operand permits Num, Ref, Nest, Kept {
    }

    /**
     * @param digits an integer
     */
    record Num(String digits) implements Operand {
    }

    /**
     * @param kind   an item or a prefab
     * @param id     the item's id or the prefab's name
     * @param member the goo type extracted from it, null for its whole value
     */
    record Ref(SearchDropdown.Kind kind, String id, @Nullable String member) implements Operand {
    }

    /**
     * @param pieces the parenthesized terms
     */
    record Nest(List<Piece> pieces) implements Operand {
    }

    /**
     * @param term a loaded term no control builds, written as loaded
     */
    record Kept(ExpressionTerms.Term term) implements Operand {
    }

    /**
     * One term.
     *
     * @param join       how it joins the terms before it
     * @param operand    what it stands on
     * @param multiplier the number multiplying it, 1 for none
     * @param divisor    the number dividing it, 1 for none
     * @param timesAfter whether the multiplier writes after the operand, as "$x * 12", rather than before it as a
     *                   count, as "12 $x"; the term keeps the form the file wrote
     */
    record Piece(ExpressionTerms.Join join, Operand operand, String multiplier, String divisor, boolean timesAfter) {
        Piece(ExpressionTerms.Join join, Operand operand, String multiplier, String divisor) {
            this(join, operand, multiplier, divisor, false);
        }

        Piece withOperand(Operand changed) {
            return new Piece(join, changed, multiplier, divisor, timesAfter);
        }

        Piece withJoin(ExpressionTerms.Join changed) {
            return new Piece(changed, operand, multiplier, divisor, timesAfter);
        }

        // Decision ratio-holds-multiplier-and-divisor-together: a side set leaves the other standing.
        Piece withSide(Op side, String factor) {
            return side == Op.MULTIPLY ? new Piece(join, operand, factor, divisor, timesAfter)
                    : new Piece(join, operand, multiplier, factor, timesAfter);
        }

        boolean isUnscaled() {
            return ONE.equals(multiplier) && ONE.equals(divisor);
        }
    }

    /**
     * @param type   the goo type's id
     * @param pieces its amount's terms
     */
    record Row(String type, List<Piece> pieces) {
    }

    private ExpressionBuilder() {
    }

    /**
     * @return the string to write to the draft, empty while the builder holds no term or row, since the resolver
     *         throws on an empty expression
     */
    Optional<String> written() {
        String text = text();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    /**
     * @return whether the value is denied, holding no goo
     */
    boolean isDenied() {
        return denied;
    }

    /**
     * @return whether the value is per goo type, a row per type, rather than one scalar expression
     */
    boolean isPerType() {
        return !rows.isEmpty() || whole.isEmpty() && perTypeChosen;
    }

    /**
     * @param operand what the term stands on
     * @return a first term over it, unscaled
     */
    static Piece firstTerm(Operand operand) {
        return new Piece(ExpressionTerms.Join.FIRST, operand, ONE, ONE);
    }

    /**
     * Names which whole kind the loaded terms are, so a switch to another kind keeps them under it.
     *
     * @param sort the whole kind's name
     */
    void nameWholeSort(String sort) {
        wholeSort = sort;
    }

    /**
     * @param sort a whole kind's name
     * @return whether a switch to that kind restores terms it held before
     */
    boolean holdsKept(String sort) {
        return !keptWhole.getOrDefault(sort, List.of()).isEmpty();
    }

    /**
     * Makes the value per goo type, keeping its whole terms to restore should it switch back.
     */
    void switchToPerType() {
        if (isPerType()) {
            return;
        }
        changed();
        keepWhole();
        rows.addAll(keptRows);
        perTypeChosen = true;
    }

    /**
     * Makes the value one number expression, starting as the number 1 where it held none before.
     */
    void switchToWhole() {
        switchToWhole(NUMBER_SORT, List.of(firstTerm(new Num(ONE))));
    }

    /**
     * Makes the value one expression of a whole kind, keeping what the other kind held to restore should it switch
     * back.
     *
     * @param sort    the whole kind's name
     * @param starter the terms it starts from where it held none before
     */
    void switchToWhole(String sort, List<Piece> starter) {
        if (!isPerType() && !whole.isEmpty() && sort.equals(wholeSort)) {
            return;
        }
        changed();
        if (isPerType()) {
            keptRows = List.copyOf(rows);
            rows.clear();
        } else {
            keepWhole();
        }
        perTypeChosen = false;
        whole.addAll(holdsKept(sort) ? keptWhole.get(sort) : starter);
        wholeSort = sort;
    }

    private void keepWhole() {
        if (!whole.isEmpty()) {
            keptWhole.put(wholeSort, List.copyOf(whole));
        }
        whole.clear();
    }

    /**
     * Adds a term holding the number 1, joined by plus after the first.
     *
     * @return whether the term was added; per-type rows refuse it
     */
    boolean addNumber() {
        return addNumber(List.of());
    }

    /**
     * Adds a term holding the number 1 to a sum, joined by plus after the first.
     *
     * @param sum the sum's place, empty for the whole value
     * @return whether the term was added; per-type rows refuse it
     */
    boolean addNumber(List<Integer> sum) {
        return addTerm(sum, new Num(ONE));
    }

    /**
     * Scales the whole value as one sum.
     *
     * @param side   the side of its ratio the number sets
     * @param factor the number scaling it
     */
    void scaleWhole(Op side, String factor) {
        if (!isNumber(factor) || whole.isEmpty()) {
            return;
        }
        changed();
        List<Piece> inner = new ArrayList<>(whole);
        whole.clear();
        whole.add(firstTerm(new Nest(List.copyOf(inner))).withSide(side, factor.trim()));
    }

    /**
     * Inserts a term at a slot of a sum, joined by the sign given; a term landing first takes no sign and the one
     * it passes takes plus. The whole value refuses it while per-type rows stand, and a subtracted first term is
     * refused, as moveTo refuses one (decision plus-and-minus-are-draggable-buttons-in-the-control-bay).
     *
     * @param sum     the sum's place, empty for the whole value
     * @param index   the slot, before the term at that index, or the sum's size for its end
     * @param operand what the term stands on
     * @param join    plus or minus
     * @return whether the term was inserted
     */
    boolean insertAt(List<Integer> sum, int index, Operand operand, ExpressionTerms.Join join) {
        if (!canInsertAt(sum, index, join)) {
            return refuse(sum.isEmpty() && !rows.isEmpty() ? ROWS_REFUSE_TERMS : MOVE_CHANGES_VALUE);
        }
        changed();
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        pieces.add(index, new Piece(join, operand, ONE, ONE));
        replaceSum(sum, signed(pieces));
        return true;
    }

    /**
     * Whether insertAt would take a term of that sign at the slot, so a drag shows only a drop the builder takes
     * (decision drags-indicate-only-a-drop-the-builder-takes).
     *
     * @param sum   the sum's place, empty for the whole value
     * @param index the slot
     * @param join  plus or minus
     * @return whether the insert lands
     */
    boolean canInsertAt(List<Integer> sum, int index, ExpressionTerms.Join join) {
        // Decision every-term-carries-its-sign-as-a-segment: a term inserted first keeps its sign.
        boolean beside = sum.isEmpty() && !rows.isEmpty();
        boolean inSum = index >= 0 && index <= sumAt(sum).size();
        return !beside && inSum && join != ExpressionTerms.Join.FIRST;
    }

    /**
     * @param path    the term's place
     * @param operand what the term stands on in place of its own, its join and ratio kept
     */
    void setOperand(List<Integer> path, Operand operand) {
        editPiece(path, piece -> piece.withOperand(operand));
    }

    /**
     * Makes a term a parenthetical holding it alone, its ratio moving inside with it.
     * Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators.
     *
     * @param path the term's place
     */
    void wrap(List<Integer> path) {
        editPiece(path, piece -> new Piece(piece.join(), new Nest(List.of(piece.withJoin(
                ExpressionTerms.Join.FIRST))), ONE, ONE));
    }

    /**
     * @param path the term's place
     * @param by   how many places later, negative for earlier
     * @return whether the move stays inside the sum; every term carries its sign, the first included, so a move keeps
     *     the sum's value (decision every-term-carries-its-sign-as-a-segment)
     */
    boolean canMove(List<Integer> path, int by) {
        int to = path.getLast() + by;
        return to >= 0 && to < sumAt(path.subList(0, path.size() - 1)).size();
    }

    /**
     * Moves a term within its sum, each term keeping its sign; a move past the sum's ends is refused (decision
     * builder-is-kind-plus-then-rows-of-terms-with-term-operators).
     *
     * @param path the term's place
     * @param by   how many places later, negative for earlier
     */
    void move(List<Integer> path, int by) {
        if (!canMove(path, by)) {
            refuse(MOVE_CHANGES_VALUE);
            return;
        }
        List<Integer> sum = path.subList(0, path.size() - 1);
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        int from = path.getLast();
        int to = from + by;
        changed();
        pieces.add(to, pieces.remove(from));
        replaceSum(sum, signed(pieces));
    }

    /**
     * Moves a term to a slot of any sum, its own, a parenthetical's inside it or one outside it, carrying its sign
     * (decision every-term-carries-its-sign-as-a-segment). A drop into the term itself or one that would empty its
     * sum is refused.
     * Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor.
     *
     * @param from    the term's place
     * @param toSum   the sum it drops into, as its place reads before the term leaves
     * @param toIndex the slot in that sum, before the term at that index, or the sum's size for its end
     * @return whether the term moved
     */
    boolean moveTo(List<Integer> from, List<Integer> toSum, int toIndex) {
        if (!canMoveTo(from, toSum, toIndex)) {
            return refuse(MOVE_CHANGES_VALUE);
        }
        Leaving leaving = leavingOf(from);
        List<Integer> fromSum = leaving.path().subList(0, leaving.path().size() - 1);
        int fromIndex = leaving.path().getLast();
        List<Piece> left = new ArrayList<>(sumAt(fromSum));
        left.remove(fromIndex);
        int slot = slotAfterLeaving(fromSum, fromIndex, toSum, toIndex);
        List<Integer> target = sumAfterLeaving(fromSum, fromIndex, toSum);
        Piece moving = leaving.moving();
        changed();
        replaceSum(fromSum, signed(left));
        List<Piece> landing = new ArrayList<>(sumAt(target));
        landing.add(Math.min(slot, landing.size()), moving);
        replaceSum(target, signed(landing));
        return true;
    }

    /**
     * Whether moveTo would take the drop, sharing its refusals: a drop on the term's own slots either side, into the
     * term itself, one emptying the whole value or a type row, or a whole term beside type rows (decision
     * drags-indicate-only-a-drop-the-builder-takes).
     *
     * @param from    the term's place
     * @param toSum   the sum it would drop into
     * @param toIndex the slot in that sum
     * @return whether the drop moves the term
     */
    boolean canMoveTo(List<Integer> from, List<Integer> toSum, int toIndex) {
        List<Integer> ownSum = from.subList(0, from.size() - 1);
        boolean origin = toSum.equals(ownSum) && (toIndex == from.getLast() || toIndex == from.getLast() + 1);
        if (origin) {
            return false;
        }
        Leaving leaving = leavingOf(from);
        List<Integer> fromSum = leaving.path().subList(0, leaving.path().size() - 1);
        return !dropBreaksTheTree(leaving.path(), toSum, sumAt(fromSum).size());
    }

    /**
     * What leaves its place as a term moves: the term, or the parenthetical its last term empties.
     *
     * @param path   the place that empties
     * @param moving the term that lands
     */
    private record Leaving(List<Integer> path, Piece moving) {
    }

    // Decision terms-and-type-rows-drag-to-a-trash-strip: the parenthetical its last term leaves goes too, the term
    // landing where the parenthetical would.
    private Leaving leavingOf(List<Integer> from) {
        List<Integer> fromSum = from.subList(0, from.size() - 1);
        List<Piece> source = sumAt(fromSum);
        if (source.size() == 1 && isParenthetical(fromSum)) {
            return new Leaving(fromSum, source.getFirst());
        }
        return new Leaving(from, source.get(from.getLast()));
    }

    // The slot in the target sum once the leaving term is out of its own sum.
    private static int slotAfterLeaving(List<Integer> fromSum, int fromIndex, List<Integer> toSum, int toIndex) {
        return toSum.equals(fromSum) && toIndex > fromIndex ? toIndex - 1 : toIndex;
    }

    // A sum inside a parenthetical, rather than the whole value's or a type row's.
    private boolean isParenthetical(List<Integer> sum) {
        boolean rowSum = !rows.isEmpty() && sum.size() == 1;
        return !sum.isEmpty() && !rowSum;
    }

    // A drop into the term itself, one emptying its sum, or a whole term dropped beside type rows.
    private boolean dropBreaksTheTree(List<Integer> from, List<Integer> toSum, int sourceSize) {
        boolean intoItself = toSum.size() >= from.size() && toSum.subList(0, from.size()).equals(from);
        boolean beside = toSum.isEmpty() && !rows.isEmpty();
        return intoItself || sourceSize <= 1 || beside;
    }

    // The target sum's place once the term leaves: a later sibling parenthetical moves one index earlier.
    private static List<Integer> sumAfterLeaving(List<Integer> fromSum, int fromIndex, List<Integer> toSum) {
        List<Integer> target = new ArrayList<>(toSum);
        int depth = fromSum.size();
        boolean underFromSum = toSum.size() > depth && toSum.subList(0, depth).equals(fromSum);
        if (underFromSum && toSum.get(depth) > fromIndex) {
            target.set(depth, toSum.get(depth) - 1);
        }
        return target;
    }

    // Every term keeps its sign wherever it lands: a first term added reads FIRST, a subtracted one MINUS, written as
    // a leading minus; a term leaving first place takes plus (decision every-term-carries-its-sign-as-a-segment).
    private static List<Piece> signed(List<Piece> pieces) {
        List<Piece> signed = new ArrayList<>(pieces);
        for (int index = 0; index < signed.size(); index++) {
            Piece piece = signed.get(index);
            if (index == 0) {
                signed.set(index, piece.withJoin(firstJoin(piece.join())));
            } else if (piece.join() == ExpressionTerms.Join.FIRST) {
                signed.set(index, piece.withJoin(ExpressionTerms.Join.PLUS));
            }
        }
        return signed;
    }

    // A first term's join: MINUS where subtracted, FIRST where added.
    private static ExpressionTerms.Join firstJoin(ExpressionTerms.Join join) {
        return join == ExpressionTerms.Join.MINUS ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.FIRST;
    }

    private boolean addTerm(List<Integer> sum, Operand operand) {
        return insertAt(sum, sum.isEmpty() && !rows.isEmpty() ? 0 : sumAt(sum).size(), operand,
                ExpressionTerms.Join.PLUS);
    }

    /**
     * @param piece  the term's index
     * @param digits the number the term holds in place of its operand
     */
    void setNumber(int piece, String digits) {
        setNumber(List.of(piece), digits);
    }

    /**
     * @param path   the term's place: the indices of the sums holding it, outermost first, then its own
     * @param digits the number the term holds in place of its operand
     */
    void setNumber(List<Integer> path, String digits) {
        if (isNumber(digits)) {
            editPiece(path, piece -> piece.withOperand(new Num(digits.trim())));
        }
    }

    /**
     * @param piece  the term's index
     * @param choice the item or prefab the term relates to in place of its operand, its whole value
     */
    void setRef(int piece, SearchDropdown.Choice choice) {
        setRef(List.of(piece), choice);
    }

    /**
     * @param path   the term's place
     * @param choice the item or prefab the term relates to in place of its operand, its whole value
     */
    void setRef(List<Integer> path, SearchDropdown.Choice choice) {
        editPiece(path, piece -> piece.withOperand(new Ref(choice.kind(), choice.id(), null)));
    }

    // The terms of the sum at a place: the whole value's, or a sum's inside it. Under per-type rows a place's first
    // index names the row, whose amount is the outermost sum.
    private List<Piece> sumAt(List<Integer> sum) {
        boolean inRows = !rows.isEmpty() && !sum.isEmpty();
        List<Piece> pieces = inRows ? rows.get(sum.getFirst()).pieces() : whole;
        for (int step : inRows ? sum.subList(1, sum.size()) : sum) {
            if (!(pieces.get(step).operand() instanceof Nest nest)) {
                return List.of();
            }
            pieces = nest.pieces();
        }
        return pieces;
    }

    private void replaceSum(List<Integer> sum, List<Piece> pieces) {
        if (sum.isEmpty()) {
            List<Piece> replaced = List.copyOf(pieces);
            whole.clear();
            whole.addAll(replaced);
            return;
        }
        if (!rows.isEmpty() && sum.size() == 1) {
            int row = sum.getFirst();
            rows.set(row, new Row(rows.get(row).type(), List.copyOf(pieces)));
            return;
        }
        List<Integer> outer = sum.subList(0, sum.size() - 1);
        List<Piece> holder = new ArrayList<>(sumAt(outer));
        int at = sum.getLast();
        holder.set(at, holder.get(at).withOperand(new Nest(List.copyOf(pieces))));
        replaceSum(outer, holder);
    }

    private void editPiece(List<Integer> path, UnaryOperator<Piece> edit) {
        changed();
        List<Integer> sum = path.subList(0, path.size() - 1);
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        int at = path.getLast();
        pieces.set(at, edit.apply(pieces.get(at)));
        replaceSum(sum, pieces);
    }

    /**
     * @param row  the row's index
     * @param type the goo type the row holds in place of its own
     */
    void setRowType(int row, String type) {
        if (rows.stream().anyMatch(other -> other.type().equals(type))) {
            refuse(TYPE_TAKEN);
            return;
        }
        changed();
        rows.set(row, new Row(type, rows.get(row).pieces()));
    }

    /**
     * @param shown a value as the draft's edit box shows it
     * @return the builder over it; a value the terms cannot read is kept as its text until a control replaces it
     */
    static ExpressionBuilder of(String shown) {
        ExpressionBuilder builder = new ExpressionBuilder();
        if (shown.isBlank()) {
            return builder;
        }
        Optional<ExpressionTerms.Value> value = ExpressionTerms.parseShown(shown);
        if (value.isEmpty()) {
            builder.unread = shown;
        } else if (value.get() instanceof ExpressionTerms.PerType perType) {
            perType.rows().forEach(row -> builder.rows.add(new Row(row.type(), piecesOf(row.sum()))));
        } else if (value.get() instanceof ExpressionTerms.Expression expression) {
            builder.whole.addAll(piecesOf(expression.sum()));
        } else {
            builder.denied = true;
        }
        return builder;
    }

    /**
     * @return the per-type rows
     */
    List<Row> rows() {
        return List.copyOf(rows);
    }

    /**
     * @return the whole-value terms
     */
    List<Piece> terms() {
        return List.copyOf(whole);
    }

    /**
     * @return why the last control was refused, empty where it was not
     */
    Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    /**
     * @return the string the file grammar reads: a per-type object, an expression, or denied
     */
    String text() {
        if (unread != null) {
            return unread;
        }
        if (denied) {
            return DENIED;
        }
        if (!rows.isEmpty()) {
            return rows.stream().map(row -> QUOTE + row.type() + KEY_END + writtenOf(row.pieces()) + QUOTE)
                    .collect(Collectors.joining(ROW_SEPARATOR, OBJECT_START, OBJECT_END));
        }
        return writtenOf(whole);
    }

    /**
     * Adds a row for a goo type, its amount starting as the number 1.
     *
     * @param type the goo type's id
     * @return whether the row was added; whole-value terms refuse it
     */
    boolean addType(String type) {
        if (!whole.isEmpty()) {
            return refuse(TERMS_REFUSE_ROWS);
        }
        if (rows.stream().anyMatch(row -> row.type().equals(type))) {
            return refuse(TYPE_TAKEN);
        }
        changed();
        rows.add(new Row(type, List.of(firstTerm(new Num(ONE)))));
        return true;
    }

    /**
     * Moves a type row to a row slot; its own slots either side leave the rows standing
     * (decision type-rows-drag-by-a-rail-to-reorder-or-trash).
     *
     * @param row  the row's index
     * @param slot the slot it drops into: before the row at that index, or the rows' count for the end
     */
    void moveRow(int row, int slot) {
        if (!canMoveRow(row, slot)) {
            return;
        }
        changed();
        Row moving = rows.remove(row);
        rows.add(slot > row ? slot - 1 : slot, moving);
    }

    /**
     * @param row  the row's index
     * @param slot the slot it would drop into
     * @return whether moveRow would reorder the rows: false on its own slots either side
     */
    boolean canMoveRow(int row, int slot) {
        return slot != row && slot != row + 1 && slot >= 0 && slot <= rows.size();
    }

    /**
     * @param row the row's index
     * @return whether removeRow would delete it: any row it holds, the last one included
     */
    boolean canRemoveRow(int row) {
        return row >= 0 && row < rows.size();
    }

    /**
     * Removes a type row; the last one goes too, leaving the value per type with no rows, as a fresh per-type value
     * reads (decision only-term-or-row-drags-to-the-trash).
     *
     * @param row the row's index
     */
    void removeRow(int row) {
        if (!canRemoveRow(row)) {
            return;
        }
        changed();
        rows.remove(row);
        perTypeChosen = true;
    }

    /**
     * @param row    the row's index
     * @param digits the amount
     */
    void setRowNumber(int row, String digits) {
        if (isNumber(digits)) {
            setRowOperand(row, new Num(digits.trim()));
        }
    }

    /**
     * @param row    the row's index
     * @param prefab the scalar prefab the amount reads
     */
    void setRowVar(int row, String prefab) {
        setRowOperand(row, new Ref(SearchDropdown.Kind.PREFAB, prefab, null));
    }

    /**
     * @param row    the row's index
     * @param choice the item or prefab the amount reads
     * @param member the goo type taken from it
     */
    void setRowRelative(int row, SearchDropdown.Choice choice, String member) {
        setRowOperand(row, new Ref(choice.kind(), choice.id(), member));
    }

    /**
     * @param row    the row's index
     * @param member the goo type the amount takes from its item or prefab in place of its own
     */
    void setRowMember(int row, String member) {
        if (rows.get(row).pieces().getFirst().operand() instanceof Ref ref) {
            setRowOperand(row, new Ref(ref.kind(), ref.id(), member));
        }
    }

    /**
     * @param row    the row's index
     * @param side   the side of the ratio the factor sets
     * @param factor the factor
     */
    void setRowScale(int row, Op side, String factor) {
        if (isNumber(factor)) {
            Row edited = rows.get(row);
            rows.set(row, new Row(edited.type(), scaled(edited.pieces(), 0, side, factor.trim())));
        }
    }

    private void setRowOperand(int row, Operand operand) {
        changed();
        Row edited = rows.get(row);
        List<Piece> pieces = new ArrayList<>(edited.pieces());
        pieces.set(0, pieces.getFirst().withOperand(operand));
        rows.set(row, new Row(edited.type(), List.copyOf(pieces)));
    }

    /**
     * Adds a whole-value term relating the value to an item or prefab, joined by plus after the first.
     *
     * @param choice the item or prefab
     * @return whether the term was added; per-type rows refuse it
     */
    boolean relate(SearchDropdown.Choice choice) {
        return relate(List.of(), choice);
    }

    /**
     * Adds a term to a sum relating it to an item or prefab, joined by plus after the first.
     *
     * @param sum    the sum's place, empty for the whole value
     * @param choice the item or prefab
     * @return whether the term was added; per-type rows refuse it
     */
    boolean relate(List<Integer> sum, SearchDropdown.Choice choice) {
        return addTerm(sum, new Ref(choice.kind(), choice.id(), null));
    }

    /**
     * @param piece the term's index
     * @param count how many of the operand the value holds
     */
    void setCount(int piece, String count) {
        setScale(piece, Op.MULTIPLY, count);
    }

    /**
     * @param piece  the term's index
     * @param member the goo type taken from the operand, null for its whole value
     */
    void setMember(int piece, @Nullable String member) {
        setMember(List.of(piece), member);
    }

    /**
     * @param path   the term's place
     * @param member the goo type taken from the operand, null for its whole value
     */
    void setMember(List<Integer> path, @Nullable String member) {
        if (pieceAt(path).operand() instanceof Ref ref) {
            editPiece(path, piece -> piece.withOperand(new Ref(ref.kind(), ref.id(), member)));
        }
    }

    /**
     * @param piece  the term's index
     * @param side   the side of the ratio the factor sets
     * @param factor the factor
     */
    void setScale(int piece, Op side, String factor) {
        setScale(List.of(piece), side, factor);
    }

    /**
     * Scales a term; the whole value standing as one sum scaled 1:1 comes apart into its terms, since its
     * parentheses then mean nothing.
     *
     * @param path   the term's place
     * @param side   the side of the ratio the factor sets
     * @param factor the factor
     */
    void setScale(List<Integer> path, Op side, String factor) {
        if (!isNumber(factor)) {
            return;
        }
        // Decision factor-left-at-one-or-set-to-one-or-zero-erases-its-segment: a side set to 0 or 1 returns to 1,
        // so the file writes no factor for it.
        String trimmed = new java.math.BigInteger(factor.trim()).compareTo(java.math.BigInteger.ONE) <= 0 ? ONE
                : factor.trim();
        if (comesApart(path, side, trimmed) && whole.getFirst().operand() instanceof Nest nest) {
            changed();
            whole.clear();
            whole.addAll(nest.pieces());
            return;
        }
        editPiece(path, piece -> piece.withSide(side, trimmed));
    }

    // Whether the scale leaves the whole value one parenthetical scaled 1:1.
    private boolean comesApart(List<Integer> path, Op side, String factor) {
        boolean wholeAlone = rows.isEmpty() && path.size() == 1 && whole.size() == 1;
        return wholeAlone && whole.getFirst().withSide(side, factor).isUnscaled();
    }

    /**
     * @param piece the term's index
     * @param join  plus or minus
     */
    void setJoin(int piece, ExpressionTerms.Join join) {
        setJoin(List.of(piece), join);
    }

    /**
     * Sets a term's sign, a sum's first term's included, which a minus writes as a leading minus (decision
     * every-term-carries-its-sign-as-a-segment).
     *
     * @param path the term's place
     * @param join plus or minus
     */
    void setJoin(List<Integer> path, ExpressionTerms.Join join) {
        if (join != ExpressionTerms.Join.FIRST) {
            editPiece(path, piece -> piece.withJoin(path.getLast() == 0 ? firstJoin(join) : join));
        }
    }

    /**
     * @param piece the term's index
     */
    void remove(int piece) {
        remove(List.of(piece));
    }

    /**
     * Removes a term; the last term of the whole value or of a type row goes, leaving an empty sum, and a
     * parenthetical losing its last term goes with it (decisions terms-and-type-rows-drag-to-a-trash-strip,
     * only-term-or-row-drags-to-the-trash).
     *
     * @param path the term's place
     */
    void remove(List<Integer> path) {
        List<Integer> sum = path.subList(0, path.size() - 1);
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        if (pieces.size() == 1 && isParenthetical(sum)) {
            remove(sum);
            return;
        }
        changed();
        pieces.remove((int) path.getLast());
        replaceSum(sum, signed(pieces));
    }

    /**
     * Whether remove would delete the term: every term goes, the last of the whole value or a type row leaving an
     * empty sum and a parenthetical's last going as its parenthetical does; the term taking first place keeps its
     * sign (decisions only-term-or-row-drags-to-the-trash, every-term-carries-its-sign-as-a-segment).
     *
     * @param path the term's place
     * @return whether removing it deletes something
     */
    boolean canRemove(List<Integer> path) {
        return path.getLast() >= 0 && path.getLast() < sumAt(path.subList(0, path.size() - 1)).size();
    }

    private Piece pieceAt(List<Integer> path) {
        return sumAt(path.subList(0, path.size() - 1)).get(path.getLast());
    }

    private List<Piece> scaled(List<Piece> pieces, int index, Op side, String factor) {
        changed();
        List<Piece> edited = new ArrayList<>(pieces);
        edited.set(index, edited.get(index).withSide(side, factor));
        return List.copyOf(edited);
    }

    private boolean isNumber(String text) {
        if (DIGITS.matcher(text.trim()).matches()) {
            return true;
        }
        refuse(NOT_A_NUMBER);
        return false;
    }

    private boolean refuse(String reason) {
        refusal = reason;
        return false;
    }

    private void changed() {
        refusal = null;
        denied = false;
        unread = null;
    }

    /**
     * @param piece a term
     * @return its operand alone as a sum, for its curated view
     */
    static ExpressionTerms.Sum operandSum(Piece piece) {
        return sumOf(List.of(firstTerm(piece.operand())));
    }

    /**
     * @param row a per-type row
     * @return its amount's terms as a sum, for its curated view
     */
    static ExpressionTerms.Sum rowSum(Row row) {
        return sumOf(row.pieces());
    }

    private static String writtenOf(List<Piece> pieces) {
        return pieces.isEmpty() ? NOTHING : ExpressionTerms.write(sumOf(pieces));
    }

    // A subtracted first term writes as a leading minus on its first factor, which the game's grammar reads as that
    // factor negated (decision every-term-carries-its-sign-as-a-segment).
    private static ExpressionTerms.Sum sumOf(List<Piece> pieces) {
        List<ExpressionTerms.Term> terms = new ArrayList<>(pieces.stream().map(ExpressionBuilder::termOf).toList());
        if (!terms.isEmpty() && terms.getFirst().join() == ExpressionTerms.Join.MINUS) {
            List<ExpressionTerms.Factor> factors = new ArrayList<>(terms.getFirst().factors());
            ExpressionTerms.Factor lead = factors.getFirst();
            factors.set(0, new ExpressionTerms.Factor(lead.scale(), new ExpressionTerms.Negated(lead.atom())));
            terms.set(0, new ExpressionTerms.Term(ExpressionTerms.Join.FIRST, List.copyOf(factors)));
        }
        return new ExpressionTerms.Sum(List.copyOf(terms));
    }

    // A multiplier writes before its operand as a count, as "3 $iron_ingot" does, save beside a number, which
    // "5 * 2" keeps apart; a divisor writes after it, so 2:3 writes "2 $x / 3" and "5 * 2 / 3".
    private static ExpressionTerms.Term termOf(Piece piece) {
        if (piece.operand() instanceof Kept kept) {
            return new ExpressionTerms.Term(piece.join(), kept.term().factors());
        }
        ExpressionTerms.Atom atom = atomOf(piece.operand());
        ExpressionTerms.Number multiplier = new ExpressionTerms.Number(piece.multiplier());
        List<ExpressionTerms.Factor> factors = new ArrayList<>();
        if (ONE.equals(piece.multiplier())) {
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.FIRST, atom));
        } else if (atom instanceof ExpressionTerms.Number || piece.timesAfter()) {
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.FIRST, atom));
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.TIMES, multiplier));
        } else {
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.FIRST, multiplier));
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.IMPLICIT, atom));
        }
        if (!ONE.equals(piece.divisor())) {
            factors.add(new ExpressionTerms.Factor(ExpressionTerms.Scale.DIVIDE,
                    new ExpressionTerms.Number(piece.divisor())));
        }
        return new ExpressionTerms.Term(piece.join(), List.copyOf(factors));
    }

    private static ExpressionTerms.Atom atomOf(Operand operand) {
        return switch (operand) {
            case Num num -> new ExpressionTerms.Number(num.digits());
            case Ref ref -> ref.kind() == SearchDropdown.Kind.PREFAB
                    ? new ExpressionTerms.Prefab(ref.id(), ref.member())
                    : new ExpressionTerms.Item(bare(ref.id()), ref.member());
            case Nest nest -> new ExpressionTerms.Group(sumOf(nest.pieces()));
            case Kept kept -> new ExpressionTerms.Group(new ExpressionTerms.Sum(List.of(kept.term())));
        };
    }

    // The file spells a minecraft item by its path alone.
    private static String bare(String id) {
        return id.startsWith(DEFAULT_NAMESPACE) ? id.substring(DEFAULT_NAMESPACE.length()) : id;
    }

    // A leading minus reads as the first term's MINUS sign on the factor it negates.
    private static List<Piece> piecesOf(ExpressionTerms.Sum sum) {
        List<ExpressionTerms.Term> terms = new ArrayList<>(sum.terms());
        if (terms.isEmpty()) {
            return List.of();
        }
        ExpressionTerms.Term first = terms.getFirst();
        if (first.factors().getFirst().atom() instanceof ExpressionTerms.Negated negated) {
            List<ExpressionTerms.Factor> factors = new ArrayList<>(first.factors());
            factors.set(0, new ExpressionTerms.Factor(factors.getFirst().scale(), negated.atom()));
            terms.set(0, new ExpressionTerms.Term(ExpressionTerms.Join.MINUS, List.copyOf(factors)));
        }
        return terms.stream().map(ExpressionBuilder::pieceOf).toList();
    }

    // A term reads as a piece where it holds the shape termOf writes; any other shape is kept whole.
    private static Piece pieceOf(ExpressionTerms.Term term) {
        List<ExpressionTerms.Factor> factors = term.factors();
        ExpressionTerms.Factor last = factors.getLast();
        boolean divided = factors.size() > 1 && last.scale() == ExpressionTerms.Scale.DIVIDE
                && last.atom() instanceof ExpressionTerms.Number;
        String divisor = divided ? ((ExpressionTerms.Number) last.atom()).digits() : ONE;
        List<ExpressionTerms.Factor> multiplied = divided ? factors.subList(0, factors.size() - 1) : factors;
        return multipliedOf(term.join(), multiplied).map(piece -> piece.withSide(Op.DIVIDE, divisor))
                .orElseGet(() -> new Piece(term.join(), new Kept(term), ONE, ONE));
    }

    // The operand alone, a count before it, as "3 $iron_ingot", or a number times another, as "5 * 2".
    private static Optional<Piece> multipliedOf(ExpressionTerms.Join join, List<ExpressionTerms.Factor> factors) {
        if (factors.size() == 1) {
            return operandOf(factors.getFirst().atom()).map(operand -> new Piece(join, operand, ONE, ONE));
        }
        if (factors.size() != PAIR) {
            return Optional.empty();
        }
        return factors.getFirst().atom() instanceof ExpressionTerms.Number count
                ? countedOf(join, count, factors.get(1)) : timesOf(join, factors.getFirst(), factors.get(1));
    }

    // Decision operand-times-number-terms-edit-like-every-term: an operand times a number, as
    // "$saturation.jelly * 12", is the operand with that number as its multiplier.
    private static Optional<Piece> timesOf(ExpressionTerms.Join join, ExpressionTerms.Factor operand,
                                           ExpressionTerms.Factor second) {
        if (second.scale() != ExpressionTerms.Scale.TIMES || !(second.atom() instanceof ExpressionTerms.Number times)) {
            return Optional.empty();
        }
        return operandOf(operand.atom()).map(atom -> new Piece(join, atom, times.digits(), ONE, true));
    }

    private static Optional<Piece> countedOf(ExpressionTerms.Join join, ExpressionTerms.Number count,
                                             ExpressionTerms.Factor second) {
        boolean numbered = second.atom() instanceof ExpressionTerms.Number;
        if (second.scale() == ExpressionTerms.Scale.IMPLICIT && !numbered) {
            return operandOf(second.atom()).map(operand -> new Piece(join, operand, count.digits(), ONE));
        }
        if (second.scale() == ExpressionTerms.Scale.TIMES && second.atom() instanceof ExpressionTerms.Number times) {
            return Optional.of(new Piece(join, new Num(count.digits()), times.digits(), ONE));
        }
        return Optional.empty();
    }

    private static Optional<Operand> operandOf(ExpressionTerms.Atom atom) {
        return switch (atom) {
            case ExpressionTerms.Number number -> Optional.of(new Num(number.digits()));
            case ExpressionTerms.Prefab prefab -> Optional.of(new Ref(SearchDropdown.Kind.PREFAB, prefab.name(),
                    prefab.type()));
            case ExpressionTerms.Item item -> Optional.of(new Ref(SearchDropdown.Kind.ITEM, item.id(), item.type()));
            case ExpressionTerms.Group group -> Optional.of(new Nest(piecesOf(group.sum())));
            default -> Optional.empty();
        };
    }
}

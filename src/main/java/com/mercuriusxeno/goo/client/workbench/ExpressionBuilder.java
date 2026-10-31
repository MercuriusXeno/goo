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
    private static final String LAST_TERM = "A value keeps at least one term: change this one instead.";
    private static final String LAST_ROW = "A per-type value keeps at least one goo type: change this one instead.";
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
     */
    record Piece(ExpressionTerms.Join join, Operand operand, String multiplier, String divisor) {
        Piece withOperand(Operand changed) {
            return new Piece(join, changed, multiplier, divisor);
        }

        Piece withJoin(ExpressionTerms.Join changed) {
            return new Piece(changed, operand, multiplier, divisor);
        }

        // Decision ratio-holds-multiplier-and-divisor-together: a side set leaves the other standing.
        Piece withSide(Op side, String factor) {
            return side == Op.MULTIPLY ? new Piece(join, operand, factor, divisor)
                    : new Piece(join, operand, multiplier, factor);
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
     * Adds a sum of terms to the whole value as one term, its first term the operand given.
     *
     * @param first what the sum's first term stands on
     */
    void addSum(Operand first) {
        addTerm(List.of(), new Nest(List.of(firstTerm(first))));
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

    private boolean addTerm(List<Integer> sum, Operand operand) {
        if (!rows.isEmpty()) {
            return refuse(ROWS_REFUSE_TERMS);
        }
        changed();
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        pieces.add(new Piece(pieces.isEmpty() ? ExpressionTerms.Join.FIRST : ExpressionTerms.Join.PLUS, operand,
                ONE, ONE));
        replaceSum(sum, pieces);
        return true;
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

    // The terms of the sum at a place: the whole value's, or a sum's inside it.
    private List<Piece> sumAt(List<Integer> sum) {
        List<Piece> pieces = whole;
        for (int step : sum) {
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
     * @param row the row's index
     */
    void removeRow(int row) {
        if (rows.size() == 1) {
            refuse(LAST_ROW);
            return;
        }
        changed();
        rows.remove(row);
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
        String trimmed = factor.trim();
        if (path.size() == 1 && whole.size() == 1 && whole.getFirst().operand() instanceof Nest nest
                && whole.getFirst().withSide(side, trimmed).isUnscaled()) {
            changed();
            whole.clear();
            whole.addAll(nest.pieces());
            return;
        }
        editPiece(path, piece -> piece.withSide(side, trimmed));
    }

    /**
     * @param piece the term's index, past the first
     * @param join  plus or minus
     */
    void setJoin(int piece, ExpressionTerms.Join join) {
        setJoin(List.of(piece), join);
    }

    /**
     * @param path the term's place, past its sum's first
     * @param join plus or minus
     */
    void setJoin(List<Integer> path, ExpressionTerms.Join join) {
        if (path.getLast() > 0 && join != ExpressionTerms.Join.FIRST) {
            editPiece(path, piece -> piece.withJoin(join));
        }
    }

    /**
     * @param piece the term's index
     */
    void remove(int piece) {
        remove(List.of(piece));
    }

    /**
     * Removes a term; a sum keeps its last term.
     *
     * @param path the term's place
     */
    void remove(List<Integer> path) {
        List<Integer> sum = path.subList(0, path.size() - 1);
        List<Piece> pieces = new ArrayList<>(sumAt(sum));
        if (pieces.size() == 1) {
            refuse(LAST_TERM);
            return;
        }
        changed();
        int at = path.getLast();
        pieces.remove(at);
        if (at == 0) {
            pieces.set(0, pieces.getFirst().withJoin(ExpressionTerms.Join.FIRST));
        }
        replaceSum(sum, pieces);
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

    private static ExpressionTerms.Sum sumOf(List<Piece> pieces) {
        return new ExpressionTerms.Sum(pieces.stream().map(ExpressionBuilder::termOf).toList());
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
        } else if (atom instanceof ExpressionTerms.Number) {
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

    private static List<Piece> piecesOf(ExpressionTerms.Sum sum) {
        return sum.terms().stream().map(ExpressionBuilder::pieceOf).toList();
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
        return factors.size() == PAIR && factors.getFirst().atom() instanceof ExpressionTerms.Number count
                ? countedOf(join, count, factors.get(1)) : Optional.empty();
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

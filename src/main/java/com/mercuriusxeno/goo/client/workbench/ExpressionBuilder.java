package com.mercuriusxeno.goo.client.workbench;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private final List<Piece> whole = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean denied;
    private @Nullable String unread;
    private @Nullable String refusal;

    /**
     * Whether a factor multiplies or divides its operand.
     */
    enum Op {
        /** The factor multiplies, written before the operand as a count. */
        MULTIPLY,
        /** The factor divides, written after the operand. */
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
     * @param join    how it joins the terms before it
     * @param operand what it stands on
     * @param op      whether its factor multiplies or divides
     * @param factor  its factor, 1 for none
     */
    record Piece(ExpressionTerms.Join join, Operand operand, Op op, String factor) {
        Piece withOperand(Operand changed) {
            return new Piece(join, changed, op, factor);
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
            return rows.stream().map(row -> QUOTE + row.type() + KEY_END + written(row.pieces()) + QUOTE)
                    .collect(Collectors.joining(ROW_SEPARATOR, OBJECT_START, OBJECT_END));
        }
        return written(whole);
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
        rows.add(new Row(type, List.of(new Piece(ExpressionTerms.Join.FIRST, new Num(ONE), Op.MULTIPLY, ONE))));
        return true;
    }

    /**
     * @param row the row's index
     */
    void removeRow(int row) {
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
     * @param op     whether the factor multiplies or divides
     * @param factor the factor
     */
    void setRowScale(int row, Op op, String factor) {
        if (isNumber(factor)) {
            Row edited = rows.get(row);
            rows.set(row, new Row(edited.type(), scaled(edited.pieces(), 0, op, factor.trim())));
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
        if (!rows.isEmpty()) {
            return refuse(ROWS_REFUSE_TERMS);
        }
        changed();
        whole.add(new Piece(whole.isEmpty() ? ExpressionTerms.Join.FIRST : ExpressionTerms.Join.PLUS,
                new Ref(choice.kind(), choice.id(), null), Op.MULTIPLY, ONE));
        return true;
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
        if (whole.get(piece).operand() instanceof Ref ref) {
            changed();
            whole.set(piece, whole.get(piece).withOperand(new Ref(ref.kind(), ref.id(), member)));
        }
    }

    /**
     * @param piece  the term's index
     * @param op     whether the factor multiplies or divides
     * @param factor the factor
     */
    void setScale(int piece, Op op, String factor) {
        if (isNumber(factor)) {
            List<Piece> edited = scaled(whole, piece, op, factor.trim());
            whole.clear();
            whole.addAll(edited);
        }
    }

    /**
     * @param piece the term's index, past the first
     * @param join  plus or minus
     */
    void setJoin(int piece, ExpressionTerms.Join join) {
        if (piece > 0 && join != ExpressionTerms.Join.FIRST) {
            changed();
            Piece edited = whole.get(piece);
            whole.set(piece, new Piece(join, edited.operand(), edited.op(), edited.factor()));
        }
    }

    /**
     * Wraps a run of terms in parentheses, as one term.
     *
     * @param from the first term's index
     * @param to   the last term's index
     */
    void wrap(int from, int to) {
        changed();
        List<Piece> inner = new ArrayList<>(whole.subList(from, to + 1));
        ExpressionTerms.Join join = inner.getFirst().join();
        Piece first = inner.getFirst();
        inner.set(0, new Piece(ExpressionTerms.Join.FIRST, first.operand(), first.op(), first.factor()));
        whole.subList(from, to + 1).clear();
        whole.add(from, new Piece(join, new Nest(List.copyOf(inner)), Op.MULTIPLY, ONE));
    }

    /**
     * @param piece the term's index
     */
    void remove(int piece) {
        changed();
        whole.remove(piece);
        if (piece == 0 && !whole.isEmpty()) {
            Piece next = whole.getFirst();
            whole.set(0, new Piece(ExpressionTerms.Join.FIRST, next.operand(), next.op(), next.factor()));
        }
    }

    private List<Piece> scaled(List<Piece> pieces, int index, Op op, String factor) {
        changed();
        List<Piece> edited = new ArrayList<>(pieces);
        Piece piece = edited.get(index);
        edited.set(index, new Piece(piece.join(), piece.operand(), op, factor));
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
        return sumOf(List.of(new Piece(ExpressionTerms.Join.FIRST, piece.operand(), Op.MULTIPLY, ONE)));
    }

    /**
     * @param row a per-type row
     * @return its amount's terms as a sum, for its curated view
     */
    static ExpressionTerms.Sum rowSum(Row row) {
        return sumOf(row.pieces());
    }

    private static String written(List<Piece> pieces) {
        return pieces.isEmpty() ? NOTHING : ExpressionTerms.write(sumOf(pieces));
    }

    private static ExpressionTerms.Sum sumOf(List<Piece> pieces) {
        return new ExpressionTerms.Sum(pieces.stream().map(ExpressionBuilder::termOf).toList());
    }

    // A multiplying factor writes before its operand as a count, as "3 $iron_ingot" does, save beside a number,
    // which "5 * 2" keeps apart; a dividing factor writes after it.
    private static ExpressionTerms.Term termOf(Piece piece) {
        if (piece.operand() instanceof Kept kept) {
            return new ExpressionTerms.Term(piece.join(), kept.term().factors());
        }
        ExpressionTerms.Atom atom = atomOf(piece.operand());
        ExpressionTerms.Factor first = new ExpressionTerms.Factor(ExpressionTerms.Scale.FIRST, atom);
        if (ONE.equals(piece.factor())) {
            return new ExpressionTerms.Term(piece.join(), List.of(first));
        }
        ExpressionTerms.Number factor = new ExpressionTerms.Number(piece.factor());
        if (piece.op() == Op.DIVIDE || atom instanceof ExpressionTerms.Number) {
            return new ExpressionTerms.Term(piece.join(), List.of(first, new ExpressionTerms.Factor(
                    piece.op() == Op.DIVIDE ? ExpressionTerms.Scale.DIVIDE : ExpressionTerms.Scale.TIMES, factor)));
        }
        return new ExpressionTerms.Term(piece.join(), List.of(
                new ExpressionTerms.Factor(ExpressionTerms.Scale.FIRST, factor),
                new ExpressionTerms.Factor(ExpressionTerms.Scale.IMPLICIT, atom)));
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

    // A term reads as a piece where it is an operand alone, a count before it, or a factor dividing it; any other
    // shape is kept whole.
    private static Piece pieceOf(ExpressionTerms.Term term) {
        List<ExpressionTerms.Factor> factors = term.factors();
        Optional<Piece> read = factors.size() == 1 ? operandOf(factors.getFirst().atom())
                .map(operand -> new Piece(term.join(), operand, Op.MULTIPLY, ONE))
                : factors.size() == PAIR ? pairOf(term.join(), factors.get(0), factors.get(1)) : Optional.empty();
        return read.orElseGet(() -> new Piece(term.join(), new Kept(term), Op.MULTIPLY, ONE));
    }

    // A count before its operand, as "3 $iron_ingot", or an operand a number divides, as "bucket.metal / 2".
    private static Optional<Piece> pairOf(ExpressionTerms.Join join, ExpressionTerms.Factor first,
                                          ExpressionTerms.Factor second) {
        if (first.atom() instanceof ExpressionTerms.Number count && second.scale() == ExpressionTerms.Scale.IMPLICIT
                && !(second.atom() instanceof ExpressionTerms.Number)) {
            return operandOf(second.atom()).map(operand -> new Piece(join, operand, Op.MULTIPLY, count.digits()));
        }
        if (second.scale() == ExpressionTerms.Scale.DIVIDE && second.atom() instanceof ExpressionTerms.Number divisor) {
            return operandOf(first.atom()).map(operand -> new Piece(join, operand, Op.DIVIDE, divisor.digits()));
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

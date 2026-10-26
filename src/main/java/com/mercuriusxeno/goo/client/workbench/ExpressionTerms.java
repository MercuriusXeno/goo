package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypes;
import org.jspecify.annotations.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An entry's value as terms, with no Screen: one expression, a sum of signed terms each an atom scaled by the numbers
 * and prefabs multiplying or dividing it, or a per-type object of one sum per goo type, split from the text as the
 * game's expression tokenizer and parser read it, and written back to a string holding the same tokens, so the
 * loader values it the same; the curated view draws these terms in place of the text (decision
 * curated-view-shows-no-brace-quote-or-raw-id).
 */
public final class ExpressionTerms {
    private static final char REFERENCE_MARK = '$';
    private static final char TYPE_MARK = '.';
    private static final char NAMESPACE_MARK = ':';
    private static final char OPEN = '(';
    private static final char CLOSE = ')';
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String TIMES = "*";
    private static final String DIVIDE = "/";
    private static final String OPEN_TOKEN = "(";
    private static final String CLOSE_TOKEN = ")";
    private static final String DENIED = "denied";
    private static final String OPERATORS = "()+-*/";
    private static final String NOTHING = "";
    private static final String ID_PUNCTUATION = "_:./-";
    private static final int NO_DOT = -1;
    private static final Set<String> GOO_TYPES = GooTypes.BUNDLED.stream().map(GooTypes::id)
            .collect(Collectors.toUnmodifiableSet());

    private ExpressionTerms() {
    }

    /**
     * One operand of a product.
     */
    public sealed interface Atom permits Number, Prefab, Item, Group, Negated {
    }

    /**
     * @param digits the integer as the text spells it
     */
    public record Number(String digits) implements Atom {
    }

    /**
     * @param name the prefab's name
     * @param type the goo type its dot suffix extracts, null for the whole prefab
     */
    public record Prefab(String name, @Nullable String type) implements Atom {
    }

    /**
     * @param id   the item's id as the text spells it
     * @param type the goo type its dot suffix extracts, null for the whole item
     */
    public record Item(String id, @Nullable String type) implements Atom {
    }

    /**
     * @param sum the parenthesized sum
     */
    public record Group(Sum sum) implements Atom {
    }

    /**
     * @param atom the atom a unary minus negates
     */
    public record Negated(Atom atom) implements Atom {
    }

    /**
     * How a term joins the sum: the first stands alone, the rest add or subtract.
     */
    public enum Join {
        /** The sum's first term. */
        FIRST(""),
        /** A term added. */
        PLUS(" + "),
        /** A term subtracted. */
        MINUS(" - ");

        private final String gap;

        Join(String gap) {
            this.gap = gap;
        }

        /**
         * @return the text written before the term
         */
        String gap() {
            return gap;
        }
    }

    /**
     * How a factor joins its product: the first stands alone, a number's neighbour multiplies by juxtaposition,
     * and the rest multiply or divide.
     */
    public enum Scale {
        /** The product's first factor. */
        FIRST(""),
        /** A factor multiplying by juxtaposition, as "3 $low" does. */
        IMPLICIT(" "),
        /** A factor multiplying. */
        TIMES(" * "),
        /** A factor dividing. */
        DIVIDE(" / ");

        private final String gap;

        Scale(String gap) {
            this.gap = gap;
        }

        /**
         * @return the text written before the factor
         */
        String gap() {
            return gap;
        }
    }

    /**
     * @param scale how the factor joins its product
     * @param atom  the factor
     */
    public record Factor(Scale scale, Atom atom) {
    }

    /**
     * @param join    how the term joins its sum
     * @param factors the product's factors in order
     */
    public record Term(Join join, List<Factor> factors) {
    }

    /**
     * @param terms the sum's terms in order
     */
    public record Sum(List<Term> terms) {
    }

    /**
     * An entry's whole value.
     */
    public sealed interface Value permits Expression, PerType, Denied {
    }

    /**
     * @param sum the expression's sum
     */
    public record Expression(Sum sum) implements Value {
    }

    /**
     * @param type the goo type's id
     * @param sum  its amount
     */
    public record TypeRow(String type, Sum sum) {
    }

    /**
     * @param rows one sum per goo type, in the object's order
     */
    public record PerType(List<TypeRow> rows) implements Value {
    }

    /**
     * The entry's value is denied.
     */
    public record Denied() implements Value {
    }

    /**
     * @param id an id
     * @return whether it names one of the mod's goo types
     */
    public static boolean isGooType(String id) {
        return GOO_TYPES.contains(id);
    }

    /**
     * @param text an expression
     * @return its sum, empty where the text holds a character, an operand or a parenthesis the game's parser refuses
     */
    public static Optional<Sum> parse(String text) {
        Optional<List<String>> tokens = tokens(text);
        if (tokens.isEmpty() || tokens.get().isEmpty()) {
            return Optional.empty();
        }
        Parser parser = new Parser(tokens.get());
        Optional<Sum> sum = parser.sum();
        return parser.done() ? sum : Optional.empty();
    }

    /**
     * @param sum a sum
     * @return the expression holding its tokens, operators spaced so no id swallows one
     */
    public static String write(Sum sum) {
        StringBuilder text = new StringBuilder();
        for (Term term : sum.terms()) {
            text.append(term.join().gap());
            for (Factor factor : term.factors()) {
                text.append(factor.scale().gap()).append(write(factor.atom()));
            }
        }
        return text.toString();
    }

    private static String write(Atom atom) {
        return switch (atom) {
            case Number number -> number.digits();
            case Prefab prefab -> REFERENCE_MARK + prefab.name() + typeSuffix(prefab.type());
            case Item item -> item.id() + typeSuffix(item.type());
            case Group group -> OPEN + write(group.sum()) + CLOSE;
            case Negated negated -> MINUS + write(negated.atom());
        };
    }

    private static String typeSuffix(@Nullable String type) {
        return type == null ? NOTHING : TYPE_MARK + type;
    }

    /**
     * @param element an entry's or constant's value as the file holds it
     * @return its terms, empty where any expression in it fails to parse
     */
    public static Optional<Value> parseValue(JsonElement element) {
        if (element.isJsonObject()) {
            return parsePerType(element.getAsJsonObject());
        }
        if (!element.isJsonPrimitive()) {
            return Optional.empty();
        }
        if (DENIED.equals(element.getAsString())) {
            return Optional.of(new Denied());
        }
        return parse(element.getAsString()).map(Expression::new);
    }

    private static Optional<Value> parsePerType(JsonObject object) {
        List<TypeRow> rows = new ArrayList<>();
        for (Map.Entry<String, JsonElement> row : object.entrySet()) {
            Optional<Sum> sum = row.getValue().isJsonPrimitive()
                    ? parse(row.getValue().getAsString()) : Optional.empty();
            if (sum.isEmpty()) {
                return Optional.empty();
            }
            rows.add(new TypeRow(row.getKey(), sum.get()));
        }
        return Optional.of(new PerType(List.copyOf(rows)));
    }

    /**
     * @param shown a value as the draft's edit box shows it: an object, a number, denied or an expression unquoted
     * @return its terms, empty where it fails to parse
     */
    public static Optional<Value> parseShown(String shown) {
        String trimmed = shown.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '{') {
            try {
                return parseValue(JsonParser.parseString(trimmed));
            } catch (JsonParseException e) {
                return Optional.empty();
            }
        }
        return parseValue(new JsonPrimitive(trimmed));
    }

    /**
     * @param value terms
     * @return the value the file holds for them: a lone integer as a number, a sum as its string, a per-type object
     *         as an object
     */
    public static JsonElement writeValue(Value value) {
        if (value instanceof PerType perType) {
            JsonObject object = new JsonObject();
            perType.rows().forEach(row -> object.add(row.type(), written(row.sum())));
            return object;
        }
        return value instanceof Expression expression ? written(expression.sum()) : new JsonPrimitive(DENIED);
    }

    private static JsonElement written(Sum sum) {
        if (sum.terms().size() == 1 && sum.terms().getFirst().factors().size() == 1
                && sum.terms().getFirst().factors().getFirst().atom() instanceof Number number) {
            return new JsonPrimitive(new BigInteger(number.digits()));
        }
        return new JsonPrimitive(write(sum));
    }

    // The tokens the game's tokenizer reads, empty where it would skip a character it does not know.
    private static Optional<List<String>> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        int at = 0;
        while (at < text.length()) {
            if (Character.isWhitespace(text.charAt(at))) {
                at++;
                continue;
            }
            int end = tokenEnd(text, at);
            if (end == NO_DOT) {
                return Optional.empty();
            }
            tokens.add(text.substring(at, end));
            at = end;
        }
        return Optional.of(tokens);
    }

    // Answers where the token starting here ends, or NO_DOT where the character starts no token.
    private static int tokenEnd(String text, int at) {
        char c = text.charAt(at);
        if (OPERATORS.indexOf(c) >= 0) {
            return at + 1;
        }
        if (c == REFERENCE_MARK) {
            return scan(text, at + 1, true);
        }
        if (Character.isDigit(c)) {
            return scanDigits(text, at + 1);
        }
        return Character.isLetter(c) ? scan(text, at + 1, false) : NO_DOT;
    }

    private static int scan(String text, int start, boolean reference) {
        int end = start;
        while (end < text.length() && (reference ? isReferenceChar(text.charAt(end)) : isIdChar(text.charAt(end)))) {
            end++;
        }
        return end;
    }

    private static int scanDigits(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isReferenceChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == TYPE_MARK;
    }

    private static boolean isIdChar(char c) {
        return Character.isLetterOrDigit(c) || ID_PUNCTUATION.indexOf(c) >= 0;
    }

    private static boolean startsAtom(@Nullable String token) {
        if (token == null) {
            return false;
        }
        char c = token.charAt(0);
        return c == OPEN || c == REFERENCE_MARK || Character.isLetterOrDigit(c);
    }

    // A dot suffix naming a goo type extracts that type; any other dot stays part of the name or id.
    private static int typeDot(String name, int after) {
        int dot = name.lastIndexOf(TYPE_MARK);
        return dot > after && dot < name.length() - 1 && isGooType(name.substring(dot + 1)) ? dot : NO_DOT;
    }

    // Reads the tokens as GooValueExpression's evalExpr, evalTerm and evalAtom do.
    private static final class Parser {
        private final List<String> tokens;
        private int at;

        Parser(List<String> tokens) {
            this.tokens = tokens;
        }

        boolean done() {
            return at == tokens.size();
        }

        private @Nullable String peek() {
            return at < tokens.size() ? tokens.get(at) : null;
        }

        Optional<Sum> sum() {
            List<Term> terms = new ArrayList<>();
            Join join = Join.FIRST;
            while (true) {
                Optional<Term> term = term(join);
                if (term.isEmpty()) {
                    return Optional.empty();
                }
                terms.add(term.get());
                String next = peek();
                if (PLUS.equals(next)) {
                    join = Join.PLUS;
                } else if (MINUS.equals(next)) {
                    join = Join.MINUS;
                } else {
                    return Optional.of(new Sum(List.copyOf(terms)));
                }
                at++;
            }
        }

        private Optional<Term> term(Join join) {
            List<Factor> factors = new ArrayList<>();
            Scale scale = Scale.FIRST;
            while (true) {
                Optional<Atom> atom = atom();
                if (atom.isEmpty()) {
                    return Optional.empty();
                }
                factors.add(new Factor(scale, atom.get()));
                String next = peek();
                if (TIMES.equals(next) || DIVIDE.equals(next)) {
                    scale = TIMES.equals(next) ? Scale.TIMES : Scale.DIVIDE;
                    at++;
                } else if (startsAtom(next)) {
                    scale = Scale.IMPLICIT;
                } else {
                    return Optional.of(new Term(join, List.copyOf(factors)));
                }
            }
        }

        private Optional<Atom> atom() {
            String token = peek();
            if (token == null) {
                return Optional.empty();
            }
            at++;
            if (MINUS.equals(token)) {
                return atom().map(Negated::new);
            }
            return OPEN_TOKEN.equals(token) ? group() : operand(token);
        }

        private Optional<Atom> group() {
            Optional<Sum> inner = sum();
            if (inner.isEmpty() || !CLOSE_TOKEN.equals(peek())) {
                return Optional.empty();
            }
            at++;
            return Optional.of(new Group(inner.get()));
        }

        private static Optional<Atom> operand(String token) {
            char first = token.charAt(0);
            if (first == REFERENCE_MARK) {
                return reference(token.substring(1));
            }
            if (Character.isDigit(first)) {
                return Optional.of(new Number(token));
            }
            if (Character.isLetter(first)) {
                int dot = typeDot(token, token.indexOf(NAMESPACE_MARK));
                return Optional.of(dot < 0 ? new Item(token, null)
                        : new Item(token.substring(0, dot), token.substring(dot + 1)));
            }
            return Optional.empty();
        }

        private static Optional<Atom> reference(String name) {
            if (name.isEmpty()) {
                return Optional.empty();
            }
            int dot = typeDot(name, 0);
            return Optional.of(dot < 0 ? new Prefab(name, null)
                    : new Prefab(name.substring(0, dot), name.substring(dot + 1)));
        }
    }
}
